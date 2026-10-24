package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

/**
 * 悬浮窗「关闭」选择页（v1.14.0）：半透明中转 Activity（仿 AiLaunchActivity），
 * 从大悬浮窗的「关闭」按钮拉起。选定时回来时长／等当前任务结束／自定义／
 * 不再出现（＝关掉显示开关，设置里可重开）。选完即关窗并 finish。
 */
public class FloatCloseActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        boolean en = Strings.en();
        boolean running = TimerState.isRunning(this);
        String[] items = en
                ? new String[]{"Back in 10 minutes", "Back in 15 minutes", "Back in 30 minutes",
                        "Back in 1 hour",
                        running ? "Back when this task ends" : "Back when the next task ends",
                        "Custom…", "Never show again (re-enable in Settings)"}
                : new String[]{"10 分钟后回来", "15 分钟后回来", "30 分钟后回来", "1 小时后回来",
                        running ? "等当前任务结束回来" : "等下一个任务结束回来",
                        "自定义…", "不再出现（在设置里可重新打开）"};
        new AlertDialog.Builder(this)
                .setTitle(en ? "Close the floating window" : "关闭悬浮窗")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: FloatSnooze.snoozeFor(this, 10); break;
                        case 1: FloatSnooze.snoozeFor(this, 15); break;
                        case 2: FloatSnooze.snoozeFor(this, 30); break;
                        case 3: FloatSnooze.snoozeFor(this, 60); break;
                        case 4: FloatSnooze.snoozeUntilTaskEnd(this); break;
                        case 5: askCustom(en); return; // 自定义走二级输入框，先不 finish
                        case 6:
                            FloatingWindow.setEnabled(this, false);
                            Toast.makeText(this, en
                                    ? "Floating window off — turn it back on in Settings"
                                    : "悬浮窗已关，可在设置里重新打开", Toast.LENGTH_LONG).show();
                            break;
                        default: break;
                    }
                    finish();
                })
                .setOnCancelListener(d -> finish())
                .show();
    }

    /** 自定义分钟数（输入方式与暂停卡的自定义时长一致：数字输入框）。 */
    private void askCustom(boolean en) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(en ? "Minutes" : "分钟数");
        new AlertDialog.Builder(this)
                .setTitle(en ? "Back after how many minutes?" : "多少分钟后回来？")
                .setView(input)
                .setPositiveButton(en ? "OK" : "确定", (d, w) -> {
                    int min = 0;
                    try { min = Integer.parseInt(input.getText().toString().trim()); } catch (Exception ignored) { }
                    if (min > 0) FloatSnooze.snoozeFor(this, min);
                    finish();
                })
                .setNegativeButton(en ? "Cancel" : "取消", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }
}
