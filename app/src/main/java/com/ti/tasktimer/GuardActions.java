package com.ti.tasktimer;

import android.content.Context;
import android.content.Intent;

/**
 * 专注守卫的执行层（为日后强力版留的扩展点）：
 * 轻量版“关掉分神软件”= 把用户送回应该用的软件：
 * 任务指定了主软件（primaryApp）就跳它，没指定就跳知行自己的「当前任务页」。
 * 强力版可在此之上先执行无障碍的 Home/返回、给分神应用盖全屏遮罩，
 * FocusGuard 状态机不需要改。
 */
public final class GuardActions {
    private GuardActions() {}

    public static void redirectToTask(Context c, TaskItem task) {
        if (task != null && task.primaryApp != null && AppLister.launch(c, task.primaryApp)) {
            TimerState.logEvent(c, "专注守卫：已跳回主软件「" + AppLister.labelOf(c, task.primaryApp) + "」");
            return;
        }
        Intent i = new Intent(c, CurrentTaskActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        c.startActivity(i);
        TimerState.logEvent(c, "专注守卫：已回到「当前任务页」");
    }
}
