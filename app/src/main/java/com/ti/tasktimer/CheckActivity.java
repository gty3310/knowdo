package com.ti.tasktimer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 全屏检查页：由 AlertHelper 拉起（熄屏走 FSI，亮屏走直接启动），两种状态同一个页面。
 * 过半页：能做完 / 做不完→写下办法。
 * 结束页：做完了→回写任务完成状态并回列表；没做完→加时（+50% / +5 / +10 / +15 分钟 / 自定义）。
 */
public class CheckActivity extends Activity {
    public static final String EXTRA_TYPE = "type";

    private int type;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        type = getIntent().getIntExtra(EXTRA_TYPE, AlertHelper.TYPE_END);
        AlertHelper.cancelAlert(this);
        vibrate();

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        int pad = dp(28);
        root.setPadding(pad, dp(56), pad, pad);
        setContentView(root);

        if (type == AlertHelper.TYPE_HALF) showHalfStage1();
        else showEnd();
    }

    @Override
    protected void onDestroy() {
        FloatingWindow.setSuppressed(false); // 检查页关闭，悬浮窗可以回来了（计时还在走时）
        super.onDestroy();
    }

    private String taskName() { return TimerState.taskTitle(this); }

    // ---------- 过半页 ----------
    private void showHalfStage1() {
        clear();
        title("你的时间过了一半了");
        subtitle("任务：" + taskName() + "\n剩下的时间还够你做完吗？");
        Button ok = bigButton("能做完", Color.rgb(46, 125, 50));
        ok.setOnClickListener(v -> {
            TimerState.logEvent(this, "过半检查[" + taskName() + "]：能做完");
            finish();
        });
        root.addView(ok);
        spacer();
        Button no = bigButton("做不完", Color.rgb(198, 40, 40));
        no.setOnClickListener(v -> showHalfStage2());
        root.addView(no);
    }

    private void showHalfStage2() {
        clear();
        title("我如何做才能做完？");
        subtitle("写下你的办法，写完继续计时（计时不会暂停）。");
        final EditText input = new EditText(this);
        input.setHint("比如：先跳过细节，先把主体做完");
        input.setTextSize(18);
        input.setMinLines(3);
        input.setGravity(Gravity.TOP);
        root.addView(input);
        spacer();
        Button go = bigButton("想好了，继续", Color.rgb(46, 125, 50));
        go.setOnClickListener(v -> {
            TimerState.logEvent(this, "过半检查[" + taskName() + "]：做不完，办法=" + input.getText().toString().trim());
            finish();
        });
        root.addView(go);
    }

    // ---------- 结束页 ----------
    private void showEnd() {
        clear();
        title("时间到了");
        subtitle("我们看一下：「" + taskName() + "」做完了吗？");
        Button done = bigButton("做完了，做下一个任务", Color.rgb(46, 125, 50));
        done.setOnClickListener(v -> {
            // 回写完成状态（一次性任务置 done，每日任务记今日完成）
            TaskStore store = new TaskStore(this);
            TaskItem t = store.byId(TimerState.taskId(this));
            if (t != null) { t.markDone(); store.save(); }
            TimerState.logEvent(this, "结束检查[" + taskName() + "]：做完了");
            TimerService.stopTimer(this);
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            finish();
        });
        root.addView(done);
        spacer();
        TextView label = new TextView(this);
        label.setText("没做完？加一点时间：");
        label.setTextSize(17);
        root.addView(label);
        spacerSmall();
        int totalMin = Math.max(1, TimerState.totalSec(this) / 60);
        root.addView(extendButton("+50%（+" + Math.max(1, totalMin / 2) + " 分钟）", Math.max(1, totalMin / 2) * 60));
        spacerSmall();
        root.addView(extendButton("+5 分钟", 300));
        spacerSmall();
        root.addView(extendButton("+10 分钟", 600));
        spacerSmall();
        root.addView(extendButton("+15 分钟", 900));
        spacerSmall();
        final EditText custom = new EditText(this);
        custom.setHint("或输入想加的分钟数");
        custom.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        custom.setTextSize(17);
        root.addView(custom);
        Button customBtn = bigButton("按输入加时", Color.rgb(21, 101, 192));
        customBtn.setOnClickListener(v -> {
            try {
                int min = Integer.parseInt(custom.getText().toString().trim());
                if (min > 0) doExtend(min * 60);
            } catch (NumberFormatException ignored) { }
        });
        root.addView(customBtn);
    }

    private Button extendButton(String text, final int addSec) {
        Button b = bigButton(text, Color.rgb(21, 101, 192));
        b.setOnClickListener(v -> doExtend(addSec));
        return b;
    }

    private void doExtend(int addSec) {
        TimerState.logEvent(this, "结束检查[" + taskName() + "]：加时 " + (addSec / 60) + " 分钟");
        TimerService.extendTimer(this, addSec);
        finish();
    }

    // ---------- 小件 ----------
    private void clear() { root.removeAllViews(); }

    private void title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(30);
        t.setTextColor(Color.BLACK);
        t.setPadding(0, 0, 0, dp(10));
        root.addView(t);
    }

    private void subtitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(19);
        t.setTextColor(Color.DKGRAY);
        t.setPadding(0, 0, 0, dp(28));
        root.addView(t);
    }

    private Button bigButton(String s, int bg) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(21);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76));
        b.setLayoutParams(lp);
        return b;
    }

    private void spacer() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(20)));
        root.addView(v);
    }

    private void spacerSmall() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(10)));
        root.addView(v);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void vibrate() {
        Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vib == null) return;
        if (type == AlertHelper.TYPE_END) {
            vib.vibrate(VibrationEffect.createWaveform(new long[]{0, 400, 200, 400}, -1));
        } else {
            vib.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }
}
