package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 统一的任务编辑弹层（v1.20.0）：新建与编辑任务共用同一个底部 sheet，
 * 三个入口打开它、字段与布局完全一致——① 日历点空白处新建（开始时间预填所点
 * 时段，保存后顺手在所点时间生成计划块）；② 主页「新建任务」；③ 点任务「编辑」。
 *
 * 字段：任务名／日期与开始时间（落 deadline＋nextStartAt 既有口径）／时长／
 * 重要性（must/secondary）／重复（none/daily，与数据模型一致）／任务级 alarm
 * 模式（follow/sound/silent/off）／情景标签（多选）。
 * 编辑态另有「专注规则」「重置计时」两个入口（经 Host 回调走宿主既有对话框），
 * 笔记与闹钟仍在展开区的笔记行与闹钟 chip 上，不在本层重复。
 * 保存纪律与既有编辑一致：按 id 重取最新副本再改（freshCopy），touch 后落盘，
 * 开始时间变了重排 StartReminder；取消不落盘。
 * 底部三钮（v1.28.0 feature 8，用户亲定）：取消（左）／立刻开始（中）／保存（右）。
 * 立刻开始与保存共用同一套校验与落盘（doSave），存好后经 Host.onStartNow 由宿主
 * 走它自己的开始路径起表；不另起一套保存逻辑。
 */
public final class TaskEditSheet {
    private TaskEditSheet() { }

    public interface Host {
        /** 保存成功后回调（created=true 为新建）：宿主自己刷新列表/日历。 */
        void onSaved(TaskItem t, boolean created);
        /** 「立刻开始」：保存成功且用户要当场开始计时。默认退化为 onSaved；
         *  有既有开始路径的宿主应覆盖本方法，先刷新再走自己的开始分发，
         *  保持与点该任务 ▶ 相同的切换/继续语义。 */
        default void onStartNow(TaskItem t, boolean created) { onSaved(t, created); }
        /** 编辑态「专注规则」入口（宿主既有 dialogFocus）。 */
        void onFocusRule(TaskItem t);
        /** 编辑态「重置计时」入口（宿主既有 reset 路径）。 */
        void onResetTimer(TaskItem t);
    }

    /** 优先级归一：四档之外的（含 null）按次要论（老数据零迁移）。 */
    static String normPriority(String p) {
        if ("must".equals(p) || "important".equals(p) || "leisure".equals(p)) return p;
        return "secondary";
    }

    private static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_LONG).show();
    }

    private static String hhmm(int min) {
        return String.format(Locale.US, "%02d:%02d", min / 60, min % 60);
    }

    // ---------------- 入口 ----------------

    /** 新建。presetStartMs 非空时预填开始时间；scheduleBlock=true 时保存后在
     *  预填时间生成计划块（块长＝时长 ×1.5、5 分钟吸附，走 addScheduleBlock）。 */
    public static void showCreate(Activity act, TaskStore store, Long presetStartMs,
                                  boolean scheduleBlock, Host host) {
        TaskItem blank = new TaskItem();
        blank.id = "";
        if (presetStartMs != null && presetStartMs > 0) {
            blank.nextStartAt = TaskItem.formatNextStart(presetStartMs);
            blank.deadline = blank.nextStartAt.substring(0, 10);
        }
        show(act, store, blank, true, presetStartMs, scheduleBlock, host);
    }

    /** 编辑已有任务（task 为渲染时对象，保存时按 id 重取最新副本）。 */
    public static void showEdit(Activity act, TaskStore store, TaskItem task, Host host) {
        show(act, store, task, false, null, false, host);
    }

    // ---------------- 弹层本体 ----------------

    private static void show(final Activity act, final TaskStore store, final TaskItem src,
                             final boolean creating, final Long presetStartMs,
                             final boolean scheduleBlock, final Host host) {
        final Context ctx = act;
        final boolean en = Strings.en();
        final Handler handler = new Handler(Looper.getMainLooper());

        // ---- 可变状态（保存前都不落盘） ----
        final String[] startHolder = {src.nextStartAt};       // "YYYY-MM-DDTHH:mm"
        final String[] deadlineHolder = {src.deadline};       // "YYYY-MM-DD"
        final boolean[] startDirty = {false};
        final String[] priority = {normPriority(src.priority)};
        final String[] repeat = {"daily".equals(src.repeat) ? "daily" : "none"};
        final String[] alarmMode = {TaskItem.normAlarmMode(src.alarmMode)};
        final Set<String> scenarios = new LinkedHashSet<>(src.scenarios);
        final boolean subtaskLocked = !creating && src.hasSubtasks();

        // ---- 视图 ----
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 20));
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Ui.surface(ctx));
        sheetBg.setCornerRadii(new float[]{dp(ctx, 18), dp(ctx, 18), dp(ctx, 18), dp(ctx, 18),
                0, 0, 0, 0});
        col.setBackground(sheetBg);

        TextView head = new TextView(ctx);
        head.setText(creating ? (en ? "New task" : "新建任务") : (en ? "Edit task" : "编辑任务"));
        head.setTextSize(17);
        head.setTextColor(Ui.text(ctx));
        head.setTypeface(head.getTypeface(), android.graphics.Typeface.BOLD);
        col.addView(head);

        // 任务名
        col.addView(label(ctx, en ? "Name" : "任务名"));
        final EditText nameIn = new EditText(ctx);
        nameIn.setHint(en ? "What is it?" : "要做什么");
        if (!creating) nameIn.setText(src.title);
        nameIn.setTextSize(16);
        // 打开任务时名字完整显示（v1.22.0）：不单行横滚截断，长名字自动换行全摆出来
        nameIn.setSingleLine(false);
        nameIn.setHorizontallyScrolling(false);
        col.addView(nameIn);

        // 日期与开始时间
        col.addView(label(ctx, en ? "Date & start time" : "日期与开始时间"));
        LinearLayout timeRow = new LinearLayout(ctx);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        final Button timeBtn = new Button(ctx);
        timeBtn.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        timeRow.addView(timeBtn);
        final TextView clearTime = new TextView(ctx);
        clearTime.setText(en ? "Clear" : "清除");
        clearTime.setTextSize(13);
        clearTime.setTextColor(Ui.danger(ctx));
        clearTime.setPadding(dp(ctx, 10), dp(ctx, 6), 0, dp(ctx, 6));
        timeRow.addView(clearTime);
        col.addView(timeRow);
        final Runnable refreshTime = () -> {
            if (startHolder[0] != null) {
                timeBtn.setText(startHolder[0].replace('T', ' '));
                clearTime.setVisibility(View.VISIBLE);
            } else if (deadlineHolder[0] != null) {
                timeBtn.setText(deadlineHolder[0] + (en ? " (no start time)" : "（未定开始时间）"));
                clearTime.setVisibility(View.VISIBLE);
            } else {
                timeBtn.setText(en ? "Not set — tap to pick" : "未设置，点这里选");
                clearTime.setVisibility(View.GONE);
            }
        };
        refreshTime.run();
        clearTime.setOnClickListener(v -> {
            startHolder[0] = null;
            deadlineHolder[0] = null;
            startDirty[0] = true;
            refreshTime.run();
        });
        timeBtn.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            String base = startHolder[0] != null ? startHolder[0]
                    : (deadlineHolder[0] != null ? deadlineHolder[0] + "T09:00" : null);
            if (base != null && base.length() >= 10) {
                try {
                    String[] dp2 = base.substring(0, 10).split("-");
                    cal.set(Integer.parseInt(dp2[0]), Integer.parseInt(dp2[1]) - 1,
                            Integer.parseInt(dp2[2]));
                } catch (Exception ignored) { }
            }
            new DatePickerDialog(act, (dv, y, mo, da) -> {
                int sh = 9, sm = 0;
                String init = startHolder[0];
                if (init != null && init.length() >= 16) {
                    try {
                        String[] tp2 = init.substring(11, 16).split(":");
                        sh = Integer.parseInt(tp2[0]);
                        sm = Integer.parseInt(tp2[1]);
                    } catch (Exception ignored) { }
                } else if (presetStartMs != null && presetStartMs > 0) {
                    Calendar pc = Calendar.getInstance();
                    pc.setTimeInMillis(presetStartMs);
                    sh = pc.get(Calendar.HOUR_OF_DAY);
                    sm = pc.get(Calendar.MINUTE);
                }
                new TimePickerDialog(act, (tv, h3, m3) -> {
                    startHolder[0] = String.format(Locale.US, "%04d-%02d-%02dT%02d:%02d",
                            y, mo + 1, da, h3, m3);
                    deadlineHolder[0] = startHolder[0].substring(0, 10);
                    startDirty[0] = true;
                    refreshTime.run();
                }, sh, sm, true).show();
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        // 时长
        col.addView(label(ctx, en ? "Length (minutes)" : "时长（分钟）"));
        final EditText durIn = new EditText(ctx);
        durIn.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        durIn.setText(String.valueOf(creating ? 30 : src.durationMinutes));
        durIn.setSelectAllOnFocus(true);
        col.addView(durIn);
        if (subtaskLocked) {
            durIn.setEnabled(false);
            TextView lockNote = new TextView(ctx);
            lockNote.setText(Strings.subDurationLocked());
            lockNote.setTextSize(12);
            lockNote.setTextColor(Ui.sub(ctx));
            col.addView(lockNote);
        }

        // 重要性
        col.addView(label(ctx, en ? "Importance" : "重要性"));
        LinearLayout prioRow = new LinearLayout(ctx);
        prioRow.setOrientation(LinearLayout.HORIZONTAL);
        final TextView[] prioChips = new TextView[4];
        final String[] prioVals = {"must", "important", "secondary", "leisure"};
        final String[] prioNames = {en ? "Must do" : "必做", en ? "Important" : "重要",
                en ? "Secondary" : "次要", en ? "Leisure" : "休息"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            prioChips[i] = chip(ctx, prioNames[i], prioVals[i].equals(priority[0]));
            prioChips[i].setOnClickListener(v -> {
                priority[0] = prioVals[idx];
                for (int j = 0; j < 4; j++) setChip(prioChips[j], j == idx);
            });
            prioRow.addView(prioChips[i]);
        }
        col.addView(prioRow);

        // 重复
        col.addView(label(ctx, en ? "Repeat" : "重复"));
        LinearLayout repRow = new LinearLayout(ctx);
        repRow.setOrientation(LinearLayout.HORIZONTAL);
        final TextView[] repChips = new TextView[2];
        final String[] repVals = {"none", "daily"};
        final String[] repNames = {en ? "No repeat" : "不重复", en ? "Daily" : "每天"};
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            repChips[i] = chip(ctx, repNames[i], repVals[i].equals(repeat[0]));
            repChips[i].setOnClickListener(v -> {
                repeat[0] = repVals[idx];
                for (int j = 0; j < 2; j++) setChip(repChips[j], j == idx);
            });
            repRow.addView(repChips[i]);
        }
        col.addView(repRow);

        // alarm 模式
        col.addView(label(ctx, en ? "Alarm for this task" : "这个任务的 alarm"));
        LinearLayout almRow = new LinearLayout(ctx);
        almRow.setOrientation(LinearLayout.HORIZONTAL);
        final String[] almVals = {TaskItem.ALARM_FOLLOW, TaskItem.ALARM_SOUND,
                TaskItem.ALARM_SILENT, TaskItem.ALARM_OFF};
        final String[] almNames = {en ? "Follow global" : "跟随全局", en ? "Sound" : "有声",
                en ? "Silent" : "静默", en ? "Off" : "关闭"};
        final TextView[] almChips = new TextView[4];
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            almChips[i] = chip(ctx, almNames[i], almVals[i].equals(alarmMode[0]));
            almChips[i].setOnClickListener(v -> {
                alarmMode[0] = almVals[idx];
                for (int j = 0; j < 4; j++) setChip(almChips[j], j == idx);
            });
            almRow.addView(almChips[i]);
        }
        col.addView(almRow);
        TextView almNote = new TextView(ctx);
        almNote.setText(en
                ? "Only this task's end-of-timer and pause call-back sounds. Idle nagging still follows the global setting."
                : "只管这个任务结束时和暂停叫回时出不出声；空档催促还是听全局设置");
        almNote.setTextSize(12);
        almNote.setTextColor(Ui.sub(ctx));
        almNote.setPadding(0, dp(ctx, 4), 0, 0);
        col.addView(almNote);

        // 情景标签
        col.addView(label(ctx, en ? "Contexts" : "情景"));
        final Button scenBtn = new Button(ctx);
        scenBtn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        col.addView(scenBtn);
        final Runnable refreshScen = () -> scenBtn.setText(scenarios.isEmpty()
                ? (en ? "None — tap to pick" : "未选，点这里选")
                : String.join("、", scenarios));
        refreshScen.run();
        scenBtn.setOnClickListener(v -> pickScenarios(act, store, src, scenarios, refreshScen));

        // 编辑态的既有高级入口
        if (!creating) {
            LinearLayout moreRow = new LinearLayout(ctx);
            moreRow.setOrientation(LinearLayout.HORIZONTAL);
            moreRow.setPadding(0, dp(ctx, 14), 0, 0);
            TextView focusBtn = chip(ctx, en ? "Focus rule" : "专注规则", false);
            focusBtn.setOnClickListener(v -> {
                // 先关本层再开宿主对话框，避免两个弹层叠着
                Dialog dlg = (Dialog) focusBtn.getTag();
                if (dlg != null) dlg.dismiss();
                if (host != null) host.onFocusRule(src);
            });
            moreRow.addView(focusBtn);
            TextView resetBtn = chip(ctx, en ? "Reset timer" : "重置计时", false);
            resetBtn.setOnClickListener(v -> {
                Dialog dlg = (Dialog) resetBtn.getTag();
                if (dlg != null) dlg.dismiss();
                if (host != null) host.onResetTimer(src);
            });
            moreRow.addView(resetBtn);
            // 软件名单入口（v1.26.0）：看这个任务的三档名单、点软件改档
            TextView appListBtn = chip(ctx, en ? "App list" : "软件名单", false);
            appListBtn.setOnClickListener(v -> {
                Dialog dlg = (Dialog) appListBtn.getTag();
                if (dlg != null) dlg.dismiss();
                AppListActivity.open(act, src.id, src.title);
            });
            moreRow.addView(appListBtn);
            col.addView(moreRow);
            // AI 聊天入口（v1.22.0）：陪用户把这个任务聊成型，改法走提案卡、点应用才落盘
            TextView chatBtn = chip(ctx, en ? "Chat with AI about this task" : "和 AI 聊聊这个任务", false);
            LinearLayout chatRow = new LinearLayout(ctx);
            chatRow.setOrientation(LinearLayout.HORIZONTAL);
            chatRow.setPadding(0, dp(ctx, 10), 0, 0);
            chatBtn.setOnClickListener(v -> {
                Dialog dlg = (Dialog) chatBtn.getTag();
                if (dlg != null) dlg.dismiss();
                ChatActivity.openForTask(act, src.id);
            });
            chatRow.addView(chatBtn);
            col.addView(chatRow);
            // tag 在 dialog 建好后回填（见下方）
            focusBtn.setTag(null);
            resetBtn.setTag(null);
            chatBtn.setTag(null);
            appListBtn.setTag(null);
            col.setTag(new TextView[]{focusBtn, resetBtn, chatBtn, appListBtn});
        }

        // 底部按钮（三钮等分，v1.28.0 feature 8 用户亲定）：取消最左、立刻开始居中、保存最右
        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(ctx, 18), 0, 0);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        Button cancelBtn = new Button(ctx);
        cancelBtn.setText(en ? "Cancel" : "取消");
        cancelBtn.setLayoutParams(btnLp);
        btnRow.addView(cancelBtn);
        Button startNowBtn = new Button(ctx);
        startNowBtn.setText(en ? "Start now" : "立刻开始");
        startNowBtn.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        btnRow.addView(startNowBtn);
        Button saveBtn = new Button(ctx);
        saveBtn.setText(en ? "Save" : "保存");
        saveBtn.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        btnRow.addView(saveBtn);
        col.addView(btnRow);

        ScrollView scroll = new ScrollView(ctx);
        scroll.addView(col);

        final Dialog dlg = new Dialog(act);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setContentView(scroll);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        // 内容过高时封顶到屏幕 86%，层内滚动，别让 sheet 顶出屏幕够不着上面的字段
        dlg.setOnShowListener(d -> {
            int maxH = (int) (act.getResources().getDisplayMetrics().heightPixels * 0.86);
            if (scroll.getHeight() > maxH) {
                scroll.getLayoutParams().height = maxH;
                scroll.requestLayout();
            }
        });
        cancelBtn.setOnClickListener(v -> dlg.dismiss());
        if (!creating && col.getTag() instanceof TextView[]) {
            for (TextView b : (TextView[]) col.getTag()) b.setTag(dlg);
        }
        // 保存与立刻开始共用同一套校验＋落盘（doSave，feature 8）：startNowFlag 区分
        // 落盘后走 onSaved 还是 onStartNow，校验失败两钮行为完全一致、不起表。
        final boolean[] startNowFlag = {false};
        final Runnable doSave = () -> {
            final boolean startNow = startNowFlag[0];
            String name = nameIn.getText().toString().trim();
            if (name.isEmpty()) {
                toast(ctx, en ? "Give the task a name first" : "先给任务起个名字");
                return;
            }
            int dur = creating ? 30 : src.durationMinutes;
            if (!subtaskLocked) {
                try {
                    int m = Integer.parseInt(durIn.getText().toString().trim());
                    if (m > 0) dur = m;
                } catch (NumberFormatException ignored) { }
            }
            final TaskItem target;
            if (creating) {
                target = TaskItem.create(name);
            } else {
                store.load();
                TaskItem fresh = store.byId(src.id);
                target = fresh != null ? fresh : src;
                target.title = name;
            }
            target.priority = priority[0];
            target.repeat = repeat[0];
            if (!subtaskLocked) target.durationMinutes = dur;
            target.alarmMode = alarmMode[0];
            target.scenarios = new ArrayList<>(scenarios);
            if (startDirty[0]) {
                if ("daily".equals(repeat[0])) {
                    target.deadline = null;
                    target.deadlineTime = null;
                } else {
                    target.deadline = deadlineHolder[0];
                    // 本层改了日期：旧的「几点前做完」与新日期脱钩，清掉防错位
                    target.deadlineTime = null;
                }
                target.nextStartAt = startHolder[0];
            } else if (creating) {
                target.deadline = "daily".equals(repeat[0]) ? null : deadlineHolder[0];
                target.nextStartAt = startHolder[0];
            } else if ("daily".equals(repeat[0])) {
                // 与既有口径一致：每日任务不带单日截止
                target.deadline = null;
                target.deadlineTime = null;
            }
            target.touch();
            if (creating) store.add(target); else store.save();
            StartReminder.reschedule(ctx, store);
            dlg.dismiss();

            // 日历新建排块：开始时间仍设着、且日期就是今天才排（用户在层里清掉时间
            // 或改到别的日子，就只建任务、不排块）；时间以保存后的开始时间为准。
            final String savedStart = target.nextStartAt;
            if (creating && scheduleBlock && savedStart != null && savedStart.length() >= 16
                    && savedStart.substring(0, 10).equals(TaskItem.today())) {
                int startMin0 = 0;
                try {
                    String[] tp = savedStart.substring(11, 16).split(":");
                    startMin0 = Integer.parseInt(tp[0]) * 60 + Integer.parseInt(tp[1]);
                } catch (Exception ignored) { }
                int startMin = Math.round(startMin0 / 5f) * 5;
                int blockDur = Math.max(5, Math.round(target.durationMinutes * 1.5f));
                startMin = Math.max(0, Math.min(startMin, 24 * 60 - blockDur));
                final String bs = hhmm(startMin), be = hhmm(startMin + blockDur);
                final String tid = target.id;
                new Thread(() -> {
                    String err = SyncManager.addScheduleBlock(ctx, tid, bs, be);
                    handler.post(() -> {
                        // 排块照旧生成（保存语义不变）；立刻开始时不弹「已添加」类 toast，
                        // 由宿主开始路径的提示顶替，避免连弹两个；排块出错两模式都报。
                        if (err != null) toast(ctx, err);
                        else if (!startNow) {
                            toast(ctx, (en ? "Added and planned at " : "已添加，并排到 ") + bs + "–" + be);
                        }
                        finishSave(host, target, true, startNow);
                    });
                }).start();
            } else {
                if (!startNow) {
                    toast(ctx, creating
                            ? (en ? "Added" : "已添加")
                            : (en ? "Saved" : "已保存"));
                }
                finishSave(host, target, creating, startNow);
            }
        };
        saveBtn.setOnClickListener(v -> {
            startNowFlag[0] = false;
            doSave.run();
        });
        startNowBtn.setOnClickListener(v -> {
            startNowFlag[0] = true;
            doSave.run();
        });
        dlg.show();
    }

    /** 落盘完成的统一出口：普通保存走 onSaved，立刻开始走 onStartNow，宿主为 null 时静默。 */
    private static void finishSave(Host host, TaskItem t, boolean created, boolean startNow) {
        if (host == null) return;
        if (startNow) host.onStartNow(t, created);
        else host.onSaved(t, created);
    }

    // ---------------- 小部件 ----------------

    private static TextView label(Context ctx, String s) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(Ui.sub(ctx));
        t.setPadding(0, dp(ctx, 14), 0, dp(ctx, 4));
        return t;
    }

    private static TextView chip(Context ctx, String text, boolean selected) {
        TextView c = new TextView(ctx);
        c.setText(text);
        c.setTextSize(13);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(ctx, 13), dp(ctx, 7), dp(ctx, 13), dp(ctx, 7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(ctx, 8), 0);
        c.setLayoutParams(lp);
        setChip(c, selected);
        return c;
    }

    private static void setChip(TextView c, boolean selected) {
        Context ctx = c.getContext();
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(ctx, 17));
        if (selected) {
            g.setColor(Ui.accent(ctx));
            c.setTextColor(Ui.onAccent(ctx));
        } else {
            g.setColor(Ui.surface(ctx));
            g.setStroke(1, Ui.line(ctx));
            c.setTextColor(Ui.text(ctx));
        }
        c.setBackground(g);
    }

    /** 情景多选：已知集合口径与既有 dialogScenarios 一致（常用三项＋全店并集＋本任务已有），带新增入口。 */
    private static void pickScenarios(final Activity act, final TaskStore store,
                                      final TaskItem src, final Set<String> sel,
                                      final Runnable onDone) {
        LinkedHashSet<String> knownSet = new LinkedHashSet<>();
        knownSet.add("有网");
        knownSet.add("无网");
        knownSet.add("跟 AI 聊天");
        knownSet.addAll(store.allScenarios());
        knownSet.addAll(sel);
        final List<String> known = new ArrayList<>(knownSet);
        // 临时集：点「完成」才写回工作集，取消不留痕（与既有 dialogScenarios 同语义）
        final Set<String> tmp = new LinkedHashSet<>(sel);
        boolean[] checked = new boolean[known.size()];
        for (int i = 0; i < known.size(); i++) checked[i] = tmp.contains(known.get(i));
        new AlertDialog.Builder(act)
                .setTitle(Strings.en() ? "Contexts (multi-select)" : "情景（可多选）")
                .setMultiChoiceItems(known.toArray(new String[0]), checked,
                        (d, which, isChecked) -> {
                            if (isChecked) tmp.add(known.get(which));
                            else tmp.remove(known.get(which));
                        })
                .setPositiveButton(Strings.en() ? "Done" : "完成", (d, w) -> {
                    sel.clear();
                    sel.addAll(tmp);
                    onDone.run();
                })
                .setNeutralButton(Strings.en() ? "+ New context" : "＋新增情景", (d, w) -> {
                    final EditText input = new EditText(act);
                    input.setHint(Strings.en() ? "e.g. At the academy" : "如：在书院、早上");
                    new AlertDialog.Builder(act)
                            .setTitle(Strings.en() ? "New context" : "新增情景")
                            .setView(input)
                            .setPositiveButton(Strings.en() ? "Add" : "添加", (d2, w2) -> {
                                String s = input.getText().toString().trim();
                                if (!s.isEmpty()) {
                                    sel.add(s);
                                    onDone.run();
                                }
                            })
                            .setNegativeButton(Strings.en() ? "Cancel" : "取消", null)
                            .show();
                })
                .setNegativeButton(Strings.en() ? "Cancel" : "取消", null)
                .show();
    }
}
