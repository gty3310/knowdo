package com.ti.tasktimer;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;

/**
 * 暂停卡流程（v1.11.0 暂停模式，替换 v1.10.0 的选时长菜单）：
 * 所有「对正在计时的任务本身点暂停」的入口统一走 PauseCard——先选目的（emoji 大格子）、
 * 再选时长、静音开关、预览确认。Activity 上下文（主页任务行、展开面板 chips、日历面板）
 * 走本类的 AlertDialog；非 Activity 上下文（悬浮窗 MINI/EDIT）走 PauseCard 的 overlay，
 * 两边同一张卡。确认后调用方走既有原语 TimerService.pauseWithReminder（带目的三件套）。
 * 取消卡＝什么都不做（不暂停）。
 *
 * 注意（v1.16.1 定案）：切换任务不经本类——开始另一个任务是直接切换（原任务停表落盘、
 * 不进暂停态），只有「暂停当前任务本身」才弹这张卡。
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
}
