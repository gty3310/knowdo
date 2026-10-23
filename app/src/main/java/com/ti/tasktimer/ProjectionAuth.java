package com.ti.tasktimer;

import android.content.Intent;

/**
 * MediaProjection 截图授权的暂存处：MainActivity / PermActivity 拿到系统授权结果后存这里，
 * ScreenCaptureService 启动时取走并创建 MediaProjection。
 * 安卓 14+ 规定一份授权结果只能创建一次投影，所以 take 后即清空；
 * 授权被用掉或被系统回收后，下一次计时开始时再请求一次（这是系统硬规定，绕不过）。
 */
public final class ProjectionAuth {
    private static int resultCode;
    private static Intent data;
    private static boolean has;

    private ProjectionAuth() {}

    public static synchronized void store(int rc, Intent d) {
        resultCode = rc;
        // 存原始 Intent 引用：授权凭证在 Intent 内部，复制可能丢凭证，不做拷贝
        data = d;
        has = d != null;
    }

    public static synchronized boolean has() { return has; }

    public static synchronized int resultCode() { return resultCode; }

    /** 取走授权数据（取后即清空，因为只能用一次）。 */
    public static synchronized Intent takeData() {
        Intent d = data;
        data = null;
        has = false;
        return d;
    }
}
