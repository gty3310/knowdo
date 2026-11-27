package com.ti.tasktimer

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Health Connect 官方客户端桥（v2.0.0）：全仓唯一的 Kotlin 文件。
 * connect-client 是 Kotlin 库，读数据的接口是 suspend 函数，从 Java 无法干净
 * 调用，故在此用 runBlocking 包成同步方法给 Java 侧（HealthStore）用；
 * 所有方法都只能在后台线程调用。只读四类：睡眠（含分期）、静息心率、
 * 步数、活动热量。原始数据只出手机本地（JSON 串回给 HealthStore 落本地
 * 缓存），永不经此上传——上云的只有 HealthStore 算出的每日汇总。
 */
object HealthConnectBridge {

    /** SDK 状态：HealthConnectClient.SDK_AVAILABLE / SDK_UNAVAILABLE / SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED。 */
    @JvmStatic
    fun sdkStatus(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    @JvmStatic
    fun isAvailable(context: Context): Boolean =
        sdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /** 本版申请的全部读权限（不多要一类）。 */
    @JvmStatic
    fun readPermissions(): Array<String> = arrayOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    )

    /** 系统授权页契约：入参＝想要的权限集，回调＝实际授予集。 */
    @JvmStatic
    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    @JvmStatic
    fun grantedPermissions(context: Context): Array<String> = runBlocking {
        HealthConnectClient.getOrCreate(context).permissionController
            .getGrantedPermissions().toTypedArray()
    }

    @JvmStatic
    fun hasAllPermissions(context: Context): Boolean {
        if (!isAvailable(context)) return false
        val want = readPermissions().toSet()
        return grantedPermissions(context).toSet().containsAll(want)
    }

    /**
     * 读 [startMs, endMs) 内的四类原始数据，返回 JSON 串：
     * {sessions:[{startMs,endMs,source,stages:[{startMs,endMs,type}]}],
     *  rhr:[{timeMs,bpm}], steps:[{startMs,endMs,count}], kcal:[{startMs,endMs,kcal}]}
     * 失败抛异常，Java 侧兜底（未授权/无数据时 App 其余功能零影响）。
     */
    @JvmStatic
    fun readJson(context: Context, startMs: Long, endMs: Long): String = runBlocking {
        val client = HealthConnectClient.getOrCreate(context)
        val range = TimeRangeFilter.between(
            Instant.ofEpochMilli(startMs), Instant.ofEpochMilli(endMs)
        )
        val out = JSONObject()

        val sessions = JSONArray()
        val sleepResp = client.readRecords(
            ReadRecordsRequest(SleepSessionRecord::class, timeRangeFilter = range)
        )
        for (rec in sleepResp.records) {
            val o = JSONObject()
            o.put("startMs", rec.startTime.toEpochMilli())
            o.put("endMs", rec.endTime.toEpochMilli())
            o.put("source", rec.metadata.dataOrigin.packageName)
            val stages = JSONArray()
            for (st in rec.stages) {
                val so = JSONObject()
                so.put("startMs", st.startTime.toEpochMilli())
                so.put("endMs", st.endTime.toEpochMilli())
                so.put("type", st.stage)
                stages.put(so)
            }
            o.put("stages", stages)
            sessions.put(o)
        }
        out.put("sessions", sessions)

        val rhr = JSONArray()
        val rhrResp = client.readRecords(
            ReadRecordsRequest(RestingHeartRateRecord::class, timeRangeFilter = range)
        )
        for (rec in rhrResp.records) {
            val o = JSONObject()
            o.put("timeMs", rec.time.toEpochMilli())
            o.put("bpm", rec.beatsPerMinute)
            rhr.put(o)
        }
        out.put("rhr", rhr)

        val steps = JSONArray()
        val stepsResp = client.readRecords(
            ReadRecordsRequest(StepsRecord::class, timeRangeFilter = range)
        )
        for (rec in stepsResp.records) {
            val o = JSONObject()
            o.put("startMs", rec.startTime.toEpochMilli())
            o.put("endMs", rec.endTime.toEpochMilli())
            o.put("count", rec.count)
            steps.put(o)
        }
        out.put("steps", steps)

        val kcal = JSONArray()
        val kcalResp = client.readRecords(
            ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, timeRangeFilter = range)
        )
        for (rec in kcalResp.records) {
            val o = JSONObject()
            o.put("startMs", rec.startTime.toEpochMilli())
            o.put("endMs", rec.endTime.toEpochMilli())
            o.put("kcal", rec.energy.inCalories)
            kcal.put(o)
        }
        out.put("kcal", kcal)

        out.toString()
    }
}
