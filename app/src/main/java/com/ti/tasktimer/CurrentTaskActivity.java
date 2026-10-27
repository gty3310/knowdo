package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * 「当前任务页」：专注守卫把人送回知行时的落点（任务没指定主软件时）。
 * 极简：任务名 + 大字倒计时 + 一大块笔记区，没有其他内容；
 * 底部两个按钮：回到任务列表 / 回到 Muse（Muse 应用由用户选一次，记住包名）。
 */
public class CurrentTaskActivity extends Activity {
    private TextView timeView;
    private EditText noteEdit;
    private boolean noteDirty = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable loop = new Runnable() {
        @Override public void run() {
            refreshTime();
            handler.postDelayed(this, 500);
        }
    };
    private final Runnable saveLoop = new Runnable() {
        @Override public void run() {
            if (noteDirty) saveNote();
            handler.postDelayed(this, 3_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 243, 236));
        int pad = dp(24);
        root.setPadding(pad, dp(48), pad, pad);
        setContentView(root);

        boolean running = TimerState.isRunning(this);

        TextView title = new TextView(this);
        title.setText(running ? TimerState.taskTitle(this) : "当前没有进行中的任务");
        title.setTextSize(26);
        title.setTextColor(Color.rgb(31, 47, 42));
        root.addView(title);

        timeView = new TextView(this);
        timeView.setTextSize(54);
        timeView.setTextColor(Color.rgb(31, 47, 42));
        timeView.setPadding(0, dp(8), 0, dp(16));
        root.addView(timeView);

        noteEdit = new EditText(this);
        noteEdit.setHint("在这里写笔记……（自动保存）");
        noteEdit.setTextSize(17);
        noteEdit.setGravity(android.view.Gravity.TOP);
        TaskItem t = currentTask();
        if (t != null && t.note != null) noteEdit.setText(t.note);
        noteEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { noteDirty = true; }
            @Override public void afterTextChanged(Editable s) { }
        });
        root.addView(noteEdit, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(0, dp(12), 0, 0);
        Button toList = new Button(this);
        toList.setText("回到任务列表");
        toList.setTextSize(16);
        toList.setLayoutParams(new LinearLayout.LayoutParams(0, dp(56), 1f));
        toList.setOnClickListener(v -> {
            saveNoteIfDirty();
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            finish();
        });
        bar.addView(toList);
        Button toClaude = new Button(this);
        toClaude.setText("回到 AI 助手");
        toClaude.setTextSize(16);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, dp(56), 1f);
        lp2.setMargins(dp(10), 0, 0, 0);
        toClaude.setLayoutParams(lp2);
        toClaude.setOnClickListener(v -> {
            saveNoteIfDirty();
            openClaude();
        });
        bar.addView(toClaude);
        root.addView(bar);

        refreshTime();
    }

    private TaskItem currentTask() {
        String id = TimerState.taskId(this);
        if (id == null || id.isEmpty()) return null;
        return new TaskStore(this).byId(id);
    }

    private void refreshTime() {
        if (timeView == null) return;
        if (!TimerState.isRunning(this)) {
            timeView.setText("");
            return;
        }
        long remainSec = Math.max(0, (TimerState.endAt(this) - System.currentTimeMillis()) / 1000);
        timeView.setText(remainSec >= 3600
                ? String.format("%d:%02d:%02d", remainSec / 3600, (remainSec % 3600) / 60, remainSec % 60)
                : String.format("%d:%02d", remainSec / 60, remainSec % 60));
    }

    private void openClaude() {
        String pkg = FocusGuard.musePackage(this);
        if (pkg != null && AppLister.launch(this, pkg)) return;
        // 还没选过 Muse 应用：让用户选一次并记住
        final List<AppLister.AppInfo> apps = AppLister.launchableApps(this);
        String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i).label;
        new AlertDialog.Builder(this)
                .setTitle("哪个是你的 AI 助手？选一次，我记住")
                .setItems(labels, (d, which) -> {
                    String p = apps.get(which).pkg;
                    FocusGuard.setMusePackage(this, p);
                    AppLister.launch(this, p);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveNoteIfDirty() { if (noteDirty) saveNote(); }

    private void saveNote() {
        TaskStore store = new TaskStore(this);
        TaskItem t = store.byId(TimerState.taskId(this));
        if (t != null) {
            t.note = noteEdit.getText().toString().trim();
            t.touch();
            store.save();
        }
        noteDirty = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(loop);
        handler.post(loop);
        handler.removeCallbacks(saveLoop);
        handler.post(saveLoop);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(loop);
        handler.removeCallbacks(saveLoop);
        saveNoteIfDirty();
        super.onPause();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
