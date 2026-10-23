package com.ti.tasktimer;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 专注守卫全屏问询页：计时中打开了无关软件时弹出。
 * 按钮：立即回到任务（跳回主软件/当前任务页）/ 给我 30 秒 / 给我 60 秒（到期自动跳回）/ 这是误判，本次放行。
 */
public class GuardActivity extends Activity {
    public static final String EXTRA_PKG = "fg_pkg";

    private String fgPkg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        fgPkg = getIntent().getStringExtra(EXTRA_PKG);
        if (fgPkg == null) fgPkg = "";
        AlertHelper.cancelGuard(this);
        Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vib != null) vib.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        int pad = dp(28);
        root.setPadding(pad, dp(56), pad, pad);
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("你是在做正确的任务吗？");
        title.setTextSize(28);
        title.setTextColor(Color.BLACK);
        root.addView(title);

        TextView sub = new TextView(this);
        String appLabel = AppLister.labelOf(this, fgPkg);
        sub.setText("当前任务：「" + TimerState.taskTitle(this) + "」\n你打开了「" + appLabel + "」，它不在这个任务的专注规则里。");
        sub.setTextSize(18);
        sub.setTextColor(Color.DKGRAY);
        sub.setPadding(0, dp(12), 0, dp(30));
        root.addView(sub);

        Button now = bigButton("立即回到任务", Color.rgb(46, 125, 50));
        now.setOnClickListener(v -> {
            FocusGuard.chooseImmediate(this);
            finish();
        });
        root.addView(now);
        spacer(root, 16);

        Button g30 = bigButton("给我 30 秒收尾", Color.rgb(21, 101, 192));
        g30.setOnClickListener(v -> {
            FocusGuard.chooseGrace(this, fgPkg, 30);
            finish();
        });
        root.addView(g30);
        spacer(root, 12);

        Button g60 = bigButton("给我 60 秒收尾", Color.rgb(21, 101, 192));
        g60.setOnClickListener(v -> {
            FocusGuard.chooseGrace(this, fgPkg, 60);
            finish();
        });
        root.addView(g60);
        spacer(root, 24);

        Button allow = bigButton("这是误判，本次放行它", Color.rgb(117, 117, 117));
        allow.setOnClickListener(v -> {
            FocusGuard.chooseAllow(this, fgPkg);
            finish();
        });
        root.addView(allow);
    }

    @Override
    protected void onDestroy() {
        FloatingWindow.setSuppressed(false);
        super.onDestroy();
    }

    private Button bigButton(String s, int bg) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(20);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(bg);
        b.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(70)));
        return b;
    }

    private void spacer(LinearLayout root, int h) {
        android.view.View v = new android.view.View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        root.addView(v);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
