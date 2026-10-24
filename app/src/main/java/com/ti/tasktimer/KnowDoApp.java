package com.ti.tasktimer;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/**
 * 进程级登记（v1.10.0）：本应用是否有 Activity 处于 started（前台可见）状态。
 * 任务衔接守卫的 FloatService 巡检用它避让本应用自己的页面：
 * 用户正在知行里（主页/设置/日历…）时不从巡检拉守卫页——主页由它自己的
 * onResume 钩子负责，设置等页面则根本不该被巡检打断。
 */
public class KnowDoApp extends Application {
    private static int startedCount = 0;

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity a) { startedCount++; }
            @Override public void onActivityStopped(Activity a) {
                if (startedCount > 0) startedCount--;
            }
            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override public void onActivityResumed(Activity a) { }
            @Override public void onActivityPaused(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { }
        });
    }

    /** 本应用任意 Activity 正在前台（含主页、设置、日历与各全屏页）。 */
    public static boolean ownActivityVisible() { return startedCount > 0; }
}
