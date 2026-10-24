package com.ti.tasktimer;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

/**
 * 暂停必带时长（v1.10.0）：所有暂停入口统一走的「选时长」流程。
 * - Activity 上下文（主页任务行、展开面板 chips、日历面板）：系统 AlertDialog 版（本类）；
 * - 非 Activity 上下文（悬浮窗 MINI/EDIT）：FloatingWindow 自己的 overlay 菜单，
 *   两边共用 ResumeReminder 的时长项与文案，且都没有「立刻暂停（无限期）」这一项了。
 * 选完时长后，普通暂停直接调 TimerService.pauseWithReminder（暂停+到点叫回在服务里一次办好，
 * 与悬浮窗菜单同一原语）；「暂停并切换到新任务」还要在新任务启动后经
 * {@link #armRemindAfterSwitch} 补设叫回（启动动作会 cancel 掉先前设的提醒）。
 */
public final class PauseFlow {
    public interface OnPick { void onPick(long delayMs); }

    private PauseFlow() { }

    /** 弹选时长菜单（标准项 + 自定义）。canceled 时什么都不做（不暂停）。 */
    public static void ask(final Context c, String taskTitle, final OnPick onPick) {
        String[] labels = new String[ResumeReminder.LABELS.length + 1];
        System.arraycopy(ResumeReminder.LABELS, 0, labels, 0, ResumeReminder.LABELS.length);
        labels[ResumeReminder.LABELS.length] = Strings.pauseCustom();
        new AlertDialog.Builder(c)
                .setTitle(Strings.pauseAskTitle(taskTitle))
                .setItems(labels, (d, which) -> {
                    if (which < ResumeReminder.DELAYS_MS.length) {
                        onPick.onPick(ResumeReminder.DELAYS_MS[which]);
                    } else {
                        askCustom(c, onPick);
                    }
                })
                .setNegativeButton(Strings.btnCancel(), null)
                .show();
    }

    /** 自定义时长（小时 + 分钟），与悬浮窗菜单的自定义项同口径。 */
    private static void askCustom(final Context c, final OnPick onPick) {
        View form = LayoutInflater.from(c).inflate(R.layout.dialog_pause_custom, null);
        final EditText hours = form.findViewById(R.id.pauseHours);
        final EditText minutes = form.findViewById(R.id.pauseMinutes);
        new AlertDialog.Builder(c)
                .setTitle(Strings.pauseCustomTitle())
                .setView(form)
                .setPositiveButton(Strings.btnOk(), (d, w) -> {
                    long h = parseLong(hours.getText().toString());
                    long m = parseLong(minutes.getText().toString());
                    long ms = (h * 60 + m) * 60_000L;
                    if (ms <= 0) {
                        Toast.makeText(c, "时长要大于 0", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    onPick.onPick(ms);
                })
                .setNegativeButton(Strings.btnCancel(), null)
                .show();
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
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
