package com.ti.tasktimer;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 守卫测试模式选「尺子」页（v1.21.3）：开测试前先选判定要对照的任务——
 * 测试模式的判定本质是「屏幕内容跟任务对不对得上」，没计时时没有正在跑的
 * 任务当尺子，判定只能按内容类别走、总是显「不分神」，用户无从验收。
 * 三种选法：①现有任务（TaskStore.visibleSorted：未完成在前、必要在前、
 * 期限近在前，每日任务按今天算，即今日任务优先）；②临时任务——只填标题
 * （备注可选），只存 ScreenGuardPrefs、只活在这次测试会话里，绝不写入
 * TaskStore/云端/任务列表；③不选任务——保持原口径（回落到正在计时的任务，
 * 没有就没有尺子）。选定后才真正开测试采样。
 * 入口只有 PermActivity 的测试模式开关；无障碍服务重绑等自动续开路径不弹
 * 本页、直接沿用会话里已存的选择，两条路径的判定口径一致。
 */
public class GuardTestPickActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 243, 236));
        int pad = dp(24);
        root.setPadding(pad, dp(40), pad, pad);
        // 根视图先挂上窗口（v1.24.2 修复）：此前本页把整棵视图树建完却从未
        // setContentView，窗口只剩主题的白色背景——用户点开就是整屏白屏、
        // 选不了尺子，测试模式永远开不起来。先挂根视图，后面任何一步失败，
        // 屏幕也不会空白。
        setContentView(root);
        try {
            buildPicker(root);
        } catch (Exception e) {
            ErrorReporter.report(this, "守卫测试", 0, ErrorReporter.summarize(e));
            showBuildFailure(root, e);
        }
    }

    private void buildPicker(LinearLayout root) {
        TextView title = new TextView(this);
        title.setText("先选一个任务当尺子");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(31, 47, 42));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("测试模式每 10 秒看一眼屏幕，拿看到的文字跟这个任务比对，判你是不是在做这个任务的事。选好才开始采样。");
        sub.setTextSize(15);
        sub.setTextColor(Color.DKGRAY);
        sub.setPadding(0, dp(8), 0, dp(16));
        root.addView(sub);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        for (final TaskItem t : new TaskStore(this).visibleSorted()) {
            Button b = rowButton((t.isDoneToday() ? "✓ " : "") + t.title);
            b.setOnClickListener(v -> {
                ScreenGuardPrefs.setTestTask(this, t.id, null, null);
                beginTest();
            });
            list.addView(b);
            spacer(list, 8);
        }

        TextView tempLabel = new TextView(this);
        tempLabel.setText("临时任务（只在这次测试里用，不会进你的任务列表）");
        tempLabel.setTextSize(15);
        tempLabel.setTextColor(Color.rgb(31, 47, 42));
        tempLabel.setPadding(0, dp(10), 0, dp(6));
        list.addView(tempLabel);

        final EditText tempTitle = new EditText(this);
        tempTitle.setHint("标题，如：回微信消息");
        tempTitle.setTextSize(16);
        list.addView(tempTitle);

        final EditText tempNote = new EditText(this);
        tempNote.setHint("备注（可选），如：只回肖诗婷的");
        tempNote.setTextSize(16);
        list.addView(tempNote);

        Button tempGo = rowButton("用这个临时任务开始测试");
        tempGo.setOnClickListener(v -> {
            String tt = tempTitle.getText().toString().trim();
            if (tt.isEmpty()) {
                Toast.makeText(this, "先给临时任务填个标题", Toast.LENGTH_SHORT).show();
                return;
            }
            ScreenGuardPrefs.setTestTask(this, ScreenGuardPrefs.TEST_TEMP_ID,
                    tt, tempNote.getText().toString().trim());
            beginTest();
        });
        list.addView(tempGo);
        spacer(list, 8);

        Button none = rowButton("不选任务（只按内容类别判）");
        none.setBackgroundColor(Color.rgb(117, 117, 117));
        none.setOnClickListener(v -> {
            ScreenGuardPrefs.clearTestTask(this);
            beginTest();
        });
        list.addView(none);
    }

    /** 构建失败时的兜底界面（v1.24.2）：一句说明＋返回按钮，绝不静默白屏。 */
    private void showBuildFailure(LinearLayout root, Exception e) {
        try {
            TextView t = new TextView(this);
            t.setText("选尺子页没能打开（" + e.getClass().getSimpleName()
                    + "）。先返回再试；开着测试者模式时，错误详情会亮在报错窗里。");
            t.setTextSize(16);
            t.setTextColor(Color.rgb(31, 47, 42));
            t.setPadding(0, dp(16), 0, dp(12));
            root.addView(t);
            Button back = rowButton("返回");
            back.setOnClickListener(v -> finish());
            root.addView(back);
        } catch (Exception ignored) { }
    }

    private void beginTest() {
        try {
            ScreenGuardPrefs.setTestMode(this, true);
            GuardTestMode.start(this);
        } catch (Exception e) {
            // 启动失败不许假装成功关页（v1.24.2）：上报＋提示，人留在本页可重试或返回
            ErrorReporter.report(this, "守卫测试", 0, ErrorReporter.summarize(e));
            Toast.makeText(this, "测试模式没能启动：" + e.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
            return;
        }
        finish();
    }

    private Button rowButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(17);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackgroundColor(Color.rgb(21, 101, 192));
        b.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));
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
