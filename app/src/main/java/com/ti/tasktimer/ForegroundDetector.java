package com.ti.tasktimer;

import android.content.Context;

/**
 * 前台应用检测的抽象接口（为日后强力版留的扩展点）：
 * 当前实现 UsageStatsDetector（使用情况访问权限，轮询）；
 * 强力版可另加 AccessibilityDetector（无障碍事件，即时触发），
 * 专注守卫的状态机（FocusGuard）与执行层（GuardActions）都不用改。
 */
public interface ForegroundDetector {
    /** 当前处于前台的应用包名；检测不到（无权限/无数据）返回 null。 */
    String foregroundPackage(Context c);

    /** 检测能力是否已就绪（如权限已授予）。 */
    boolean isReady(Context c);
}
