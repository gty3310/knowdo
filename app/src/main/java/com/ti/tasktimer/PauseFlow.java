package com.ti.tasktimer;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;

/**
 * 暂停卡流程（v1.11.0 暂停模式，替换 v1.10.0 的选时长菜单）：
 * 所有暂停入口统一走 PauseCard——先选目的（emoji 大格子）、再选时长、静音开关、
 * 预览确认。Activity 上下文（主页任务行、展开面板 chips、日历面板）走本类的
 * AlertDialog；非 Activity 上下文（悬浮窗 MINI/EDIT）走 PauseCard 的 overlay，
 * 两边同一张卡。确认后调用方走既有原语 TimerService.pauseWithReminder（带目的三件套）；
 * 「暂停并切换到新任务」还要在新任务启动后经 {@link #armRemindAfterSwitch} 补设叫回
 * （启动动作会 cancel 掉先前设的提醒）。取消卡＝什么都不做（不暂停）。
 */
public final class PauseFlow {
    private PauseFlow() { }

    /** 弹暂停卡。onPick 收到完整选择（时长＋目的＋emoji＋静音）后才触发暂停。 */
    public static void ask(final Context c, String taskTitle, final PauseCard.OnPick onPick) {
        final AlertDialog[] holder = new AlertDialog[1];
        View card = PauseCard.buildCard(c, taskTitle, Strings.pauseStartBtn(),
                ch -> {
                    if (holder[0] != null) holder[0].dismiss();
                    onPick.onPick(ch);
                },
                () -> { if (holder[0] != null) holder[0].dismiss(); });
        AlertDialog dlg = new AlertDialog.Builder(c)
                .setView(card)
                .setOnCancelListener(dlg2 -> { })
                .create();
        holder[0] = dlg;
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    /**
     * 「暂停当前任务并切换到新任务」的补设：新任务的开始动作会 cancel 暂停时设的叫回提醒，
     * 所以要在新任务启动之后再补设一次——先短延时兜一层。
     * 边界：如果中途插入了屏幕守卫的截图授权流程，补设可能被授权返回的再次启动吞掉；
     * 这时暂停槽数据（pausedTaskId + 剩余时长）仍在，不丢任务，只是少一次全屏叫回。
     */
    public static void armRemindAfterSwitch(final Context c, final long delayMs) {
        final Context app = c.getApplicationContext();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                () -> ResumeReminder.schedule(app, TimerState.pausedTaskId(app), delayMs), 1200);
    }
}
