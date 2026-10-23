package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 知行 KnowDo 主界面：Excel 式任务表。
 * 每行：▶ 开始计时 | 任务（点按改名）| 情景（点按多选）| 截止（点按选日期/每日重复）| 时长（点按改分钟）
 * 长按一行：标记完成 / 删除。与 Drive 的 tasks.json 通过「同步」双向合并。
 */
public class MainActivity extends Activity {
    private static final int REQ_PICK_BRIDGE = 42;

    private TaskStore store;
    private ListView listView;
    private TaskAdapter adapter;
    private TextView statusView;
    private Button realtimeBtn;
    private android.content.BroadcastReceiver syncReceiver;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable statusLoop = new Runnable() {
        @Override public void run() { refreshStatus(); handler.postDelayed(this, 1000); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new TaskStore(this);
        AlertHelper.createChannels(this);
        buildUi();
        if (getIntent() != null && getIntent().getBooleanExtra("pick_bridge", false)) {
            launchBridgePicker();
        } else if (SyncManager.getUri(this) != null) {
            runSync(false); // 打开即自动同步一次
            if (SyncManager.realtimeEnabled(this)) SyncService.start(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        store.load();
        adapter.notifyDataSetChanged();
        updateRealtimeBtn();
        handler.removeCallbacks(statusLoop);
        handler.post(statusLoop);
        if (syncReceiver == null) {
            syncReceiver = new android.content.BroadcastReceiver() {
                @Override public void onReceive(android.content.Context c, Intent i) {
                    store.load();
                    adapter.notifyDataSetChanged();
                    refreshStatus();
                }
            };
        }
        IntentFilter f = new IntentFilter(SyncService.ACTION_SYNC_UPDATED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(syncReceiver, f, RECEIVER_NOT_EXPORTED);
        else registerReceiver(syncReceiver, f);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(statusLoop);
        if (syncReceiver != null) {
            try { unregisterReceiver(syncReceiver); } catch (Exception ignored) { }
        }
        super.onPause();
    }

    // ---------------- UI 骨架 ----------------
    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(16), dp(18), dp(16), dp(6));
        TextView title = new TextView(this);
        title.setText("知行 KnowDo");
        title.setTextSize(24);
        title.setTextColor(Color.BLACK);
        head.addView(title);
        statusView = new TextView(this);
        statusView.setTextSize(13);
        statusView.setTextColor(Color.DKGRAY);
        head.addView(statusView);
        root.addView(head);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(12), dp(4), dp(12), dp(8));
        bar.addView(barButton("＋ 新增", v -> dialogNewTask()));
        bar.addView(barButton("同步", v -> {
            if (SyncManager.getUri(this) == null) launchBridgePicker();
            else runSync(true);
        }));
        realtimeBtn = barButton("", v -> toggleRealtime());
        bar.addView(realtimeBtn);
        updateRealtimeBtn();
        bar.addView(barButton("权限", v -> startActivity(new Intent(this, PermActivity.class))));
        bar.addView(barButton("记录", v -> startActivity(new Intent(this, LogActivity.class))));
        root.addView(bar);

        root.addView(headerRow());

        listView = new ListView(this);
        adapter = new TaskAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            dialogRowMenu(adapter.visible.get(position));
            return true;
        });
        root.addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        refreshStatus();
    }

    private void updateRealtimeBtn() {
        if (realtimeBtn != null) {
            realtimeBtn.setText(SyncManager.realtimeEnabled(this) ? "实时：开" : "实时：关");
        }
    }

    private void toggleRealtime() {
        boolean on = !SyncManager.realtimeEnabled(this);
        SyncManager.setRealtimeEnabled(this, on);
        updateRealtimeBtn();
        if (on) {
            if (SyncManager.getUri(this) == null) {
                toast("实时同步已开：先点「同步」选中 Drive 里的 tasks.json");
                launchBridgePicker();
            } else {
                SyncService.start(this);
                toast("实时同步已开：每 30 秒自动同步，带「立即开始」标记的任务会自动开始计时");
            }
        } else {
            SyncService.stop(this);
            toast("实时同步已关");
        }
    }

    private Button barButton(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(14);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private View headerRow() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setBackgroundColor(Color.rgb(238, 238, 238));
        h.setPadding(dp(6), dp(6), dp(6), dp(6));
        h.addView(headerCell("", 44));
        h.addView(headerCell("任务", 0));
        h.addView(headerCell("情景", 74));
        h.addView(headerCell("截止", 70));
        h.addView(headerCell("时长", 48));
        h.addView(headerCell("专注", 62));
        return h;
    }

    private TextView headerCell(String s, int widthDp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(Color.DKGRAY);
        if (widthDp == 0) t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        else t.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), LinearLayout.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private void refreshStatus() {
        if (statusView == null) return;
        StringBuilder sb = new StringBuilder();
        if (TimerState.isRunning(this)) {
            long remain = Math.max(0, (TimerState.endAt(this) - System.currentTimeMillis()) / 1000);
            sb.append("计时中：「").append(TimerState.taskTitle(this)).append("」剩余 ")
              .append(remain / 60).append(":").append(String.format("%02d", remain % 60)).append("  ");
        }
        String last = SyncManager.lastResult(this);
        sb.append(last.isEmpty() ? "点「同步」连接 Drive 的 tasks.json" : last);
        statusView.setText(sb.toString());
    }

    // ---------------- 任务行 ----------------
    private class TaskAdapter extends BaseAdapter {
        List<TaskItem> visible = new ArrayList<>();

        TaskAdapter() { reload(); }

        void reload() { visible = store.visibleSorted(); }

        @Override public int getCount() { return visible.size(); }
        @Override public Object getItem(int p) { return visible.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override public void notifyDataSetChanged() { reload(); super.notifyDataSetChanged(); }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final TaskItem t = visible.get(position);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(6), dp(8), dp(6), dp(8));
            boolean done = t.isDoneToday();
            row.setBackgroundColor(done ? Color.rgb(245, 245, 245) : Color.WHITE);

            Button play = new Button(MainActivity.this);
            play.setText(TimerState.isRunning(MainActivity.this) && t.id.equals(TimerState.taskId(MainActivity.this)) ? "■" : "▶");
            play.setTextSize(15);
            play.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(48)));
            play.setOnClickListener(v -> onPlay(t));
            row.addView(play);

            TextView titleCell = new TextView(MainActivity.this);
            String titleText = ("must".equals(t.priority) ? "★ " : "") + t.title;
            titleCell.setText(done ? titleText + " ✓" : titleText);
            titleCell.setTextSize(16);
            titleCell.setTextColor(done ? Color.GRAY : Color.BLACK);
            if (done) titleCell.setPaintFlags(titleCell.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            titleCell.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            titleCell.setOnClickListener(v -> dialogTitle(t));
            row.addView(titleCell);

            TextView scCell = new TextView(MainActivity.this);
            scCell.setText(t.scenarios.isEmpty() ? "＋情景" : String.join("、", t.scenarios));
            scCell.setTextSize(13);
            scCell.setTextColor(t.scenarios.isEmpty() ? Color.GRAY : Color.rgb(21, 101, 192));
            scCell.setLayoutParams(new LinearLayout.LayoutParams(dp(74), LinearLayout.LayoutParams.WRAP_CONTENT));
            scCell.setOnClickListener(v -> dialogScenarios(t));
            row.addView(scCell);

            TextView dlCell = new TextView(MainActivity.this);
            dlCell.setText("daily".equals(t.repeat) ? "每日" : (t.deadline == null ? "＋日期" : t.deadline.substring(5)));
            dlCell.setTextSize(13);
            dlCell.setTextColor(t.deadline == null && !"daily".equals(t.repeat) ? Color.GRAY : Color.rgb(21, 101, 192));
            dlCell.setLayoutParams(new LinearLayout.LayoutParams(dp(70), LinearLayout.LayoutParams.WRAP_CONTENT));
            dlCell.setOnClickListener(v -> dialogDeadline(t));
            row.addView(dlCell);

            TextView duCell = new TextView(MainActivity.this);
            duCell.setText(t.durationMinutes + " 分");
            duCell.setTextSize(13);
            duCell.setTextColor(Color.rgb(21, 101, 192));
            duCell.setLayoutParams(new LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.WRAP_CONTENT));
            duCell.setOnClickListener(v -> dialogDuration(t));
            row.addView(duCell);

            TextView foCell = new TextView(MainActivity.this);
            foCell.setText(focusCellText(t));
            foCell.setTextSize(12);
            foCell.setMaxLines(2);
            foCell.setTextColor("off".equals(t.focusMode) ? Color.GRAY : Color.rgb(198, 40, 40));
            foCell.setLayoutParams(new LinearLayout.LayoutParams(dp(62), LinearLayout.LayoutParams.WRAP_CONTENT));
            foCell.setOnClickListener(v -> dialogFocus(t));
            row.addView(foCell);
            return row;
        }
    }

    private String focusCellText(TaskItem t) {
        if ("block".equals(t.focusMode)) {
            return t.blockedApps.isEmpty() ? "禁（未选）"
                    : "禁:" + AppLister.labelOf(this, t.blockedApps.get(0)) + (t.blockedApps.size() > 1 ? "+" + (t.blockedApps.size() - 1) : "");
        }
        if ("allow".equals(t.focusMode)) {
            return t.allowedApps.isEmpty() ? "仅（未选）"
                    : "仅:" + AppLister.labelOf(this, t.allowedApps.get(0)) + (t.allowedApps.size() > 1 ? "+" + (t.allowedApps.size() - 1) : "");
        }
        return (t.focusRule == null || t.focusRule.isEmpty()) ? "＋专注" : "规则";
    }

    private void onPlay(final TaskItem t) {
        if (TimerState.isRunning(this)) {
            String runningId = TimerState.taskId(this);
            if (t.id.equals(runningId)) {
                toast("这个任务正在计时中");
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("已有任务在计时")
                    .setMessage("「" + TimerState.taskTitle(this) + "」正在计时，切换到「" + t.title + "」吗？")
                    .setPositiveButton("切换", (d, w) -> {
                        TimerService.stopTimer(this);
                        startTaskTimer(t);
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        startTaskTimer(t);
    }

    private void startTaskTimer(TaskItem t) {
        TimerService.startTimer(this, Math.max(1, t.durationMinutes) * 60, t.id, t.title);
        toast("开始计时：" + t.title + "（" + t.durationMinutes + " 分钟）");
        adapter.notifyDataSetChanged();
    }

    // ---------------- 编辑对话框 ----------------
    private void dialogNewTask() {
        final EditText input = new EditText(this);
        input.setHint("任务名称");
        new AlertDialog.Builder(this)
                .setTitle("新增任务")
                .setView(input)
                .setPositiveButton("添加", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    store.add(TaskItem.create(name));
                    adapter.notifyDataSetChanged();
                    toast("已添加，点各列补全情景、截止和时长");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void dialogTitle(final TaskItem t) {
        final EditText input = new EditText(this);
        input.setText(t.title);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("任务名称")
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) { t.title = name; t.touch(); store.save(); adapter.notifyDataSetChanged(); }
                })
                .setNeutralButton("设为必要/取消必要", (d, w) -> {
                    t.priority = "must".equals(t.priority) ? null : "must";
                    t.touch(); store.save(); adapter.notifyDataSetChanged();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void dialogScenarios(final TaskItem t) {
        LinkedHashSet<String> knownSet = new LinkedHashSet<>();
        knownSet.add("有网");
        knownSet.add("无网");
        knownSet.add("跟 AI 聊天");
        knownSet.addAll(store.allScenarios());
        knownSet.addAll(t.scenarios);
        final List<String> known = new ArrayList<>(knownSet);
        final Set<String> sel = new LinkedHashSet<>(t.scenarios);
        boolean[] checked = new boolean[known.size()];
        for (int i = 0; i < known.size(); i++) checked[i] = sel.contains(known.get(i));
        new AlertDialog.Builder(this)
                .setTitle("情景（可多选）")
                .setMultiChoiceItems(known.toArray(new String[0]), checked,
                        (d, which, isChecked) -> {
                            if (isChecked) sel.add(known.get(which));
                            else sel.remove(known.get(which));
                        })
                .setPositiveButton("保存", (d, w) -> {
                    t.scenarios = new ArrayList<>(sel);
                    t.touch(); store.save(); adapter.notifyDataSetChanged();
                })
                .setNeutralButton("＋新增情景", (d, w) -> {
                    final EditText input = new EditText(this);
                    input.setHint("如：在书院、早上、跟 AI 聊天");
                    new AlertDialog.Builder(this)
                            .setTitle("新增情景")
                            .setView(input)
                            .setPositiveButton("添加", (d2, w2) -> {
                                String s = input.getText().toString().trim();
                                if (!s.isEmpty() && !t.scenarios.contains(s)) {
                                    t.scenarios.add(s);
                                    t.touch(); store.save(); adapter.notifyDataSetChanged();
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void dialogDeadline(final TaskItem t) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), 0);
        final CheckBox repeatBox = new CheckBox(this);
        repeatBox.setText("每日重复任务");
        repeatBox.setChecked("daily".equals(t.repeat));
        box.addView(repeatBox);
        final DatePicker picker = new DatePicker(this);
        Calendar cal = Calendar.getInstance();
        if (t.deadline != null) {
            try {
                String[] p = t.deadline.split("-");
                cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            } catch (Exception ignored) { }
        }
        picker.init(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), null);
        box.addView(picker);
        repeatBox.setOnCheckedChangeListener((b, isChecked) -> picker.setEnabled(!isChecked));
        picker.setEnabled(!repeatBox.isChecked());
        new AlertDialog.Builder(this)
                .setTitle("截止日期")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    if (repeatBox.isChecked()) {
                        t.repeat = "daily";
                        t.deadline = null;
                    } else {
                        t.repeat = "none";
                        t.deadline = String.format("%04d-%02d-%02d",
                                picker.getYear(), picker.getMonth() + 1, picker.getDayOfMonth());
                    }
                    t.touch(); store.save(); adapter.notifyDataSetChanged();
                })
                .setNeutralButton("清除日期", (d, w) -> {
                    t.repeat = "none";
                    t.deadline = null;
                    t.touch(); store.save(); adapter.notifyDataSetChanged();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void dialogDuration(final TaskItem t) {
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(t.durationMinutes));
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("时长（分钟）")
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        int m = Integer.parseInt(input.getText().toString().trim());
                        if (m > 0) { t.durationMinutes = m; t.touch(); store.save(); adapter.notifyDataSetChanged(); }
                    } catch (NumberFormatException ignored) { }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void dialogFocus(final TaskItem t) {
        final List<AppLister.AppInfo> apps = AppLister.launchableApps(this);
        final String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i).label;
        final String[] mode = {t.focusMode == null ? "off" : t.focusMode};
        final List<String> blocked = new ArrayList<>(t.blockedApps);
        final List<String> allowed = new ArrayList<>(t.allowedApps);
        final String[] primary = {t.primaryApp};

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), 0);

        final EditText ruleInput = new EditText(this);
        ruleInput.setHint("自然语言规则，如：读经典只用时习，不要开微信和浏览器");
        ruleInput.setText(t.focusRule == null ? "" : t.focusRule);
        ruleInput.setMinLines(2);
        ruleInput.setTextSize(15);
        box.addView(ruleInput);

        final TextView summary = new TextView(this);
        summary.setTextSize(13);
        summary.setTextColor(Color.DKGRAY);
        summary.setPadding(0, dp(8), 0, dp(4));

        final Button[] modeBtns = new Button[3];
        final String[] modeVals = {"off", "block", "allow"};
        final String[] modeNames = {"不监测", "黑名单", "白名单"};
        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            for (int i = 0; i < 3; i++) {
                modeBtns[i].setBackgroundColor(modeVals[i].equals(mode[0])
                        ? Color.rgb(46, 125, 50) : Color.rgb(189, 189, 189));
                modeBtns[i].setTextColor(Color.WHITE);
            }
            summary.setText("拦截 " + blocked.size() + " 个 · 允许 " + allowed.size() + " 个 · 主软件："
                    + (primary[0] == null ? "无（跳回当前任务页）" : AppLister.labelOf(this, primary[0])));
        };
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            modeBtns[i] = new Button(this);
            modeBtns[i].setText(modeNames[i]);
            modeBtns[i].setTextSize(14);
            modeBtns[i].setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            modeBtns[i].setOnClickListener(v -> { mode[0] = modeVals[idx]; refresh[0].run(); });
            modeRow.addView(modeBtns[i]);
        }
        box.addView(modeRow);
        box.addView(summary);

        Button pickBlocked = new Button(this);
        pickBlocked.setText("选择要拦截的软件（黑名单）");
        pickBlocked.setOnClickListener(v -> pickAppsMulti("拦截这些软件", apps, labels, blocked, refresh[0]));
        box.addView(pickBlocked);
        Button pickAllowed = new Button(this);
        pickAllowed.setText("选择只允许用的软件（白名单）");
        pickAllowed.setOnClickListener(v -> pickAppsMulti("只允许这些软件", apps, labels, allowed, refresh[0]));
        box.addView(pickAllowed);
        Button pickPrimary = new Button(this);
        pickPrimary.setText("选择主软件（被拦下时跳回它）");
        pickPrimary.setOnClickListener(v -> {
            String[] items = new String[labels.length + 1];
            items[0] = "无（跳回知行的当前任务页）";
            System.arraycopy(labels, 0, items, 1, labels.length);
            new AlertDialog.Builder(this)
                    .setTitle("主软件")
                    .setItems(items, (d, which) -> {
                        primary[0] = which == 0 ? null : apps.get(which - 1).pkg;
                        refresh[0].run();
                    })
                    .show();
        });
        box.addView(pickPrimary);
        refresh[0].run();

        new AlertDialog.Builder(this)
                .setTitle("专注规则")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    t.focusRule = ruleInput.getText().toString().trim();
                    t.focusMode = mode[0];
                    t.blockedApps = new ArrayList<>(blocked);
                    t.allowedApps = new ArrayList<>(allowed);
                    t.primaryApp = primary[0];
                    t.touch();
                    store.save();
                    adapter.notifyDataSetChanged();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void pickAppsMulti(String title, List<AppLister.AppInfo> apps, String[] labels,
                               List<String> target, Runnable onDone) {
        final Set<String> sel = new LinkedHashSet<>(target);
        boolean[] checked = new boolean[apps.size()];
        for (int i = 0; i < apps.size(); i++) checked[i] = sel.contains(apps.get(i).pkg);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
                    if (isChecked) sel.add(apps.get(which).pkg);
                    else sel.remove(apps.get(which).pkg);
                })
                .setPositiveButton("确定", (d, w) -> {
                    target.clear();
                    target.addAll(sel);
                    onDone.run();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void dialogRowMenu(final TaskItem t) {
        String[] items = {t.isDoneToday() ? "取消完成标记" : "标记完成", "删除任务"};
        new AlertDialog.Builder(this)
                .setTitle(t.title)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        if (t.isDoneToday()) t.markUndone(); else t.markDone();
                        store.save(); adapter.notifyDataSetChanged();
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("删除任务")
                                .setMessage("确定删除「" + t.title + "」吗？")
                                .setPositiveButton("删除", (d2, w2) -> {
                                    store.remove(t);
                                    adapter.notifyDataSetChanged();
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    }
                })
                .show();
    }

    // ---------------- Drive 同步 ----------------
    private void launchBridgePicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_PICK_BRIDGE);
        toast("请在 Google Drive 里打开 _电子大脑/task list/ 并选中 tasks.json");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_BRIDGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception ignored) { }
            SyncManager.setUri(this, uri);
            runSync(true);
            if (SyncManager.realtimeEnabled(this)) SyncService.start(this);
        }
    }

    private void runSync(final boolean verbose) {
        new Thread(() -> {
            String result = SyncManager.syncNow(this, store);
            SyncManager.setLastResult(this, result);
            handler.post(() -> {
                store.load();
                adapter.notifyDataSetChanged();
                refreshStatus();
                if (verbose) toast(result);
            });
        }).start();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
