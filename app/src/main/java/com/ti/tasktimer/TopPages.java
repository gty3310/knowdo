package com.ti.tasktimer;

/**
 * 全屏页面在显登记（v1.10.0）：任务衔接守卫拉起前要避让这些页面——
 * 结束/过半检查页、任务闹钟响铃页、开始提醒页、暂停叫回页、衔接守卫页自身。
 * 各页在 onResume 置位、onPause 清除；进程内静态标记即可（守卫巡检与这些页同进程）。
 */
public final class TopPages {
    public static volatile boolean checkShown;
    public static volatile boolean alarmShown;
    public static volatile boolean startRemindShown;
    public static volatile boolean resumeRemindShown;
    public static volatile boolean nextShown;

    private TopPages() { }

    public static boolean anyBlockingShown() {
        return checkShown || alarmShown || startRemindShown || resumeRemindShown || nextShown;
    }
}
