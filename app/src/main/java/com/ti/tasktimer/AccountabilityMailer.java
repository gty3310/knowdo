package com.ti.tasktimer;

import android.content.Context;
import android.graphics.Bitmap;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 黄图问责邮件的发送链（v1.28.0 feature 7），全 App 唯一出口：
 * VerdictEngine 在屏幕内容守卫「终判为黄色内容」的落账点调 onNsfwConfirmed。
 *
 * 红线与例外（用户 2026-10-28 亲定）：守卫截图从不落盘、从不上传——唯一例外
 * 是本类：仅当问责开关打开时，把那一帧在内存里重度模糊＋降分辨率打码后，
 * 只把打码图发给用户自设的问责邮箱。原图不复制出调用方、不写任何文件，
 * KnowDoAccessibilityService 照旧在分析后立刻 recycle 它。
 *
 * 流程：内存打码（纯 Bitmap 运算，输入输出都不碰文件系统）→ 后台线程把打码图
 * 上传 Supabase Storage 私有桶 guard-reports 的本人文件夹 → 调 Edge Function
 * accountability-mail，由服务端按云端配置与防滥发口径（10 分钟合并、每天
 * 5 封，计数在服务端 accountability_log）决定发不发、经 Resend 发出。
 * 结果都落任务日志一行，用户事后看得见；失败不打扰用户（测试者模式下经
 * ErrorReporter 亮出，与其他模块同口径）。
 */
public final class AccountabilityMailer {
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final String BUCKET = "guard-reports";

    private AccountabilityMailer() {}

    /**
     * 终判黄图时调（VerdictEngine.trigger 唯一挂点）。第一行先判开关：
     * 关着时直接返回，绝不碰截图；测试模式不走 VerdictEngine，天然到不了这。
     */
    public static void onNsfwConfirmed(Context c, Bitmap frame, TaskItem task) {
        if (!Accountability.enabled(c)) return; // 开关关：什么都不做
        if (frame == null || frame.isRecycled()) return;
        final Context app = c.getApplicationContext();
        if (!Supa.isLoggedIn(app)) {
            TimerState.logEvent(app, "黄图问责邮件：已开启但未登录云端账号，本次未发送");
            return;
        }
        if (Accountability.recipients(app).isEmpty()) {
            TimerState.logEvent(app, "黄图问责邮件：已开启但没填问责邮箱，本次未发送");
            return;
        }
        // 当场在内存里打码（原图随后由调用方照旧销毁）；打码失败就不发，不留任何副本
        final byte[] jpg = mosaicJpeg(frame);
        if (jpg == null) {
            TimerState.logEvent(app, "黄图问责邮件：打码失败，本次未发送");
            return;
        }
        final String taskTitle = task != null ? task.title : "";
        final long occurredAt = System.currentTimeMillis();
        EXEC.execute(() -> uploadAndInvoke(app, jpg, taskTitle, occurredAt));
    }

    /**
     * 打码：先缩到极小（马赛克化），再平滑放大回最宽 360 的低清图，如此两轮——
     * 高斯式模糊＋降分辨率，内容不可辨认。全程只有 Bitmap 与内存字节流，
     * 没有任何文件路径参与（本类不引用任何文件 API）。
     */
    static byte[] mosaicJpeg(Bitmap src) {
        Bitmap tiny = null, mid = null, tiny2 = null, out = null;
        try {
            int w = src.getWidth(), h = src.getHeight();
            if (w <= 0 || h <= 0) return null;
            int tw = Math.max(6, w / 24), th = Math.max(6, h / 24);
            tiny = Bitmap.createScaledBitmap(src, tw, th, true);
            int ow = Math.min(360, w);
            int oh = Math.max(1, h * ow / w);
            mid = Bitmap.createScaledBitmap(tiny, ow, oh, true);
            tiny2 = Bitmap.createScaledBitmap(mid, Math.max(6, ow / 6), Math.max(6, oh / 6), true);
            out = Bitmap.createScaledBitmap(tiny2, ow, oh, true);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (!out.compress(Bitmap.CompressFormat.JPEG, 68, baos)) return null;
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            if (tiny != null) tiny.recycle();
            if (mid != null) mid.recycle();
            if (tiny2 != null) tiny2.recycle();
            if (out != null) out.recycle();
        }
    }

    // ---------------- 后台：上传打码图 → 调问责函数 ----------------

    private static void uploadAndInvoke(Context app, byte[] jpg, String taskTitle, long occurredAt) {
        try {
            String uid = Supa.userId(app);
            String token = Supa.accessToken(app);
            if (uid.isEmpty() || token == null) {
                TimerState.logEvent(app, "黄图问责邮件：登录已过期，本次未发送");
                return;
            }
            String path = uid + "/" + occurredAt + ".jpg";
            // 1) 上传打码图（私有桶，策略只许写自己 uid 的文件夹）
            HttpURLConnection up = (HttpURLConnection) new URL(
                    Supa.BASE + "/storage/v1/object/" + BUCKET + "/" + path).openConnection();
            up.setRequestMethod("POST");
            up.setConnectTimeout(15_000);
            up.setReadTimeout(30_000);
            up.setRequestProperty("apikey", SupaSecrets.PUBLISHABLE_KEY);
            up.setRequestProperty("Authorization", "Bearer " + token);
            up.setRequestProperty("Content-Type", "image/jpeg");
            // 不带 x-upsert：路径是 {uid}/{毫秒时间戳}.jpg 天然唯一；而 upsert 在
            // Storage 侧还要额外的 UPDATE/SELECT 策略，普通 POST 只需 INSERT 一条
            up.setDoOutput(true);
            OutputStream os = up.getOutputStream();
            os.write(jpg);
            os.close();
            int upStatus = up.getResponseCode();
            drain(up, upStatus);
            up.disconnect();
            if (upStatus < 200 || upStatus >= 300) {
                ErrorReporter.report(app, src(), upStatus, "问责图上传失败");
                TimerState.logEvent(app, "黄图问责邮件：打码图上传失败（" + upStatus + "），本次未发送");
                return;
            }
            // 2) 调问责函数：发不发由服务端按云端配置与防滥发口径决定
            JSONObject body = new JSONObject();
            body.put("path", path);
            body.put("task_title", taskTitle);
            body.put("occurred_at", occurredAt);
            HttpURLConnection fn = (HttpURLConnection) new URL(
                    Supa.BASE + "/functions/v1/accountability-mail").openConnection();
            fn.setRequestMethod("POST");
            fn.setConnectTimeout(15_000);
            fn.setReadTimeout(60_000);
            fn.setRequestProperty("apikey", SupaSecrets.PUBLISHABLE_KEY);
            fn.setRequestProperty("Authorization", "Bearer " + token);
            fn.setRequestProperty("Content-Type", "application/json");
            fn.setDoOutput(true);
            OutputStream fos = fn.getOutputStream();
            fos.write(body.toString().getBytes(StandardCharsets.UTF_8));
            fos.close();
            int fnStatus = fn.getResponseCode();
            String respText = readBody(fn, fnStatus);
            fn.disconnect();
            boolean sent = false;
            String reason = "";
            try {
                JSONObject r = new JSONObject(respText);
                sent = r.optBoolean("sent", false);
                reason = r.optString("reason", r.optString("error", ""));
            } catch (Exception ignored) { }
            if (sent) {
                TimerState.logEvent(app, "黄图问责邮件：终判黄图，已把打码截图发给问责邮箱");
            } else if ("merged_window".equals(reason)) {
                TimerState.logEvent(app, "黄图问责邮件：10 分钟内已发过一封，本次合并、未重复发");
            } else if ("daily_cap".equals(reason)) {
                TimerState.logEvent(app, "黄图问责邮件：今天已发满 5 封，本次未发");
            } else {
                ErrorReporter.report(app, src(), fnStatus, "问责函数未发送：" + reason);
                TimerState.logEvent(app, "黄图问责邮件：本次未发送（" + reason + "）");
            }
        } catch (Exception e) {
            ErrorReporter.report(app, src(), 0, ErrorReporter.summarize(e));
            TimerState.logEvent(app, "黄图问责邮件：发送链异常，本次未发送");
        }
    }

    private static String src() { return Strings.en() ? "Accountability mail" : "黄图问责邮件"; }

    private static void drain(HttpURLConnection con, int status) {
        try {
            InputStream in = status >= 400 ? con.getErrorStream() : con.getInputStream();
            if (in != null) { while (in.read() != -1) { } in.close(); }
        } catch (Exception ignored) { }
    }

    private static String readBody(HttpURLConnection con, int status) {
        try {
            InputStream in = status >= 400 ? con.getErrorStream() : con.getInputStream();
            return in == null ? "" : Supa.readAll(in);
        } catch (Exception e) {
            return "";
        }
    }
}
