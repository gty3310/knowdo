package com.ti.tasktimer;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import java.lang.ref.WeakReference;

/**
 * 进程级登记（v1.10.0）：本应用是否有 Activity 处于 started（前台可见）状态。
 * 任务衔接守卫的 FloatService 巡检用它避让本应用自己的页面：
 * 用户正在知行里（主页/设置/日历…）时不从巡检拉守卫页——主页由它自己的
 * onResume 钩子负责，设置等页面则根本不该被巡检打断。
 * v1.24.1 起另记最近 resumed 的 Activity（弱引用）：测试者模式的报错悬浮窗
 * 在无悬浮窗权限时回落挂到它上面展示。
 */
public class KnowDoApp extends Application {
    private static int startedCount = 0;
    private static WeakReference<Activity> resumedRef;

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity a) { startedCount++; }
            @Override public void onActivityStopped(Activity a) {
                if (startedCount > 0) startedCount--;
            }
            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override public void onActivityResumed(Activity a) {
                resumedRef = new WeakReference<>(a);
            }
            @Override public void onActivityPaused(Activity a) {
                if (resumedRef != null && resumedRef.get() == a) resumedRef = null;
            }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) {
                if (resumedRef != null && resumedRef.get() == a) resumedRef = null;
            }
        });
    }

    /** 本应用任意 Activity 正在前台（含主页、设置、日历与各全屏页）。 */
    public static boolean ownActivityVisible() { return startedCount > 0; }

    /** 最近 resumed 的本应用 Activity（可能已销毁/为 null，调用方须判空）。 */
    public static Activity currentActivity() {
        return resumedRef == null ? null : resumedRef.get();
    }
}
