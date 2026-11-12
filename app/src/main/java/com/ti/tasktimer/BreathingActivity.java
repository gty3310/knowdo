package com.ti.tasktimer;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 深呼吸门（v1.83.0 F3）：中等/困难档在问询页点了宽限钮之后、宽限生效
 * 之前，先在这里做完一段深呼吸。时长由 GuardActivity 按该任务当日已
 * 生效宽限次数算好（15 秒起翻倍、封顶 120 秒），经 EXTRA_BREATH_SECONDS
 * 传入。人必须留在页内：onPause（切后台、切别的软件、锁屏）即作废——
 * 本次宽限取消、计数不增，直接 finish；呼吸期满才执行原宽限动作
 * （名单 FocusGuard.chooseGrace／内容 VerdictEngine.contentGrace 原路径），
 * 并记一次已生效宽限（困难档同时占一次每日配额）。
 * 分神记录不在这里：点宽限钮那一刻已由 GuardActivity.recordDriftOnce
 * 记过，呼吸放弃不回滚（用户定案）。
 */
public class BreathingActivity extends Activity {
    public static final String EXTRA_MODE = "mode"; // "list" | "content"
    public static final String EXTRA_PKG = "pkg";
    public static final String EXTRA_CATEGORY = "category";
    public static final String EXTRA_GRACE_SECONDS = "grace_seconds";
    public static final String EXTRA_BREATH_SECONDS = "breath_seconds";
    public static final String EXTRA_TASK_ID = "task_id";

    private static final long PHASE_MS = 4_000L; // 吸气 4 秒／呼气 4 秒

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long startMs;
    private long totalMs;
    private boolean completed;
    private boolean aborted;
    private TextView countText;
    private TextView phaseText;
    private View circle;

    private String mode = "list";
    private String pkg = "";
    private String category = "";
    private String taskId = "";
    private int graceSeconds;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (completed || aborted) return;
            long elapsed = System.currentTimeMillis() - startMs;
            long remain = totalMs - elapsed;
            if (remain <= 0) { complete(); return; }
            countText.setText(String.valueOf((remain + 999) / 1000));
            // 呼吸节律：4 秒吸气（圆放大）＋4 秒呼气（圆收回），正弦缓动
            long phase = elapsed % (PHASE_MS * 2);
            boolean inhale = phase < PHASE_MS;
            double p = (phase % PHASE_MS) / (double) PHASE_MS;
            double ease = (1 - Math.cos(Math.PI * p)) / 2;
            float scale = (float) (inhale ? 1.0 + 0.35 * ease : 1.35 - 0.35 * ease);
            circle.setScaleX(scale);
            circle.setScaleY(scale);
            phaseText.setText(inhale ? Strings.breathIn() : Strings.breathOut());
            handler.postDelayed(this, 50);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FloatingWindow.setSuppressed(true);

        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) mode = "list";
        pkg = getIntent().getStringExtra(EXTRA_PKG);
        if (pkg == null) pkg = "";
        category = getIntent().getStringExtra(EXTRA_CATEGORY);
        if (category == null) category = "";
        taskId = getIntent().getStringExtra(EXTRA_TASK_ID);
        if (taskId == null) taskId = "";
        graceSeconds = getIntent().getIntExtra(EXTRA_GRACE_SECONDS, 60);
        int breathSeconds = getIntent().getIntExtra(EXTRA_BREATH_SECONDS, 15);
        totalMs = Math.max(1, breathSeconds) * 1000L;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 243, 236));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(28);
        root.setPadding(pad, dp(56), pad, pad);
        setContentView(root);

        // 志（设了才显示，小字一行，与问询页同款）
        TextView asp = Aspiration.makeView(this, false);
        if (asp != null) {
            asp.setGravity(Gravity.CENTER);
            root.addView(asp, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        spacer(root, 48);

        countText = new TextView(this);
        countText.setTextSize(72);
        countText.setTextColor(Color.rgb(31, 47, 42));
        countText.setGravity(Gravity.CENTER);
        root.addView(countText);
        spacer(root, 24);

        phaseText = new TextView(this);
        phaseText.setTextSize(20);
        phaseText.setTextColor(Color.rgb(31, 47, 42));
        phaseText.setGravity(Gravity.CENTER);
        root.addView(phaseText);
        spacer(root, 36);

        // 呼吸圆：朱砂描边的圆，随吸气放大、呼气收回
        circle = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.rgb(247, 243, 236));
        g.setStroke(dp(3), Color.rgb(217, 75, 58));
        circle.setBackground(g);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(120), dp(120));
        clp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(circle, clp);

        countText.setText(String.valueOf((totalMs + 999) / 1000));
        phaseText.setText(Strings.breathIn());
        startMs = System.currentTimeMillis();
        handler.post(tick);
    }

    /** 呼吸期满：执行原宽限动作（原路径）＋记已生效宽限＋清抑制标记。 */
    private void complete() {
        completed = true;
        handler.removeCallbacks(tick);
        Context app = getApplicationContext();
        if ("content".equals(mode)) VerdictEngine.contentGrace(app, category, graceSeconds);
        else FocusGuard.chooseGrace(app, pkg, graceSeconds);
        GuardLedger.noteGrace(this, taskId);
        if (ScreenGuardPrefs.DIFF_HARD.equals(ScreenGuardPrefs.guardDifficulty(this))) {
            GuardLedger.noteHardGrace(this);
        }
        GuardBreath.clear(this);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 人离开这一页（切后台/切别的软件/锁屏）→ 本次宽限作废：
        // 不执行宽限、计数不增，分神记录不回滚（已在点钮时记过）
        if (!completed && !aborted) {
            aborted = true;
            handler.removeCallbacks(tick);
            GuardBreath.clear(this);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(tick);
        FloatingWindow.setSuppressed(false);
        super.onDestroy();
    }

    private void spacer(LinearLayout root, int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        root.addView(v);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
