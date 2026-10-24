package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 设置页（v1.8.0 新设）：按「账号／显示／守卫／通用」四组承接原主页顶栏、权限页的全部设置与入口。
 * 红线：只是搬位置——开关都写回各自原有的存储（SyncManager / ScreenGuardPrefs / FloatingWindow /
 * Ui 本地偏好），同步与守卫语义一律不变。
 * v1.9.0：账号区状态精简为一行、桥文件行只显示已选择/未选择、「实时同步」开关整行去掉
 * （云端实时推送常开，语义见 SyncManager.realtimeEnabled）。
 */
public class SettingsActivity extends Activity {
    private static final int REQ_PICK_BRIDGE = 42;
    private TaskStore store;
    private LinearLayout root;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        store = new TaskStore(this);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        store.load();
        buildUi(); // 回到本页时状态（登录/开关/桥文件）可能已变，整页重画最稳
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    /** 桥文件的显示名（Drive 文档 Uri 取最后一段并解码，没有现成 helper）。 */
    private static String bridgeName(Uri uri) {
        String seg = uri.getLastPathSegment();
        if (seg == null) return uri.toString();
        try { return java.net.URLDecoder.decode(seg, "UTF-8"); }
        catch (Exception e) { return seg; }
    }

    // ---------------- 骨架 ----------------

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.bg(this));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(32));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText(Strings.settingsTitle());
        title.setTextSize(21);
        title.setTextColor(Ui.text(this));
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        root.addView(title);

        buildAspirationGroup();
        buildAccountGroup();
        buildDisplayGroup();
        buildGuardsGroup();
        buildMorningGroup();
        buildGeneralGroup();
    }

    // ---------------- 晨间组（v1.16.0）：晨间首填开关＋新一天起始时刻 ----------------
    // 存放走 HabitData（本地 habit_data.json ＋云端 user_state.habit_data 同步，Mac 共用一份）。

    private void buildMorningGroup() {
        groupTitle(Strings.grpMorning());
        org.json.JSONObject hroot = HabitData.load(this);
        Switch ms = mkSwitch(HabitData.morningEnabled(hroot));
        ms.setOnCheckedChangeListener((b, on) -> {
            org.json.JSONObject r = HabitData.load(this);
            HabitData.setMorning(r, on, HabitData.morningDayStartMinutes(r));
            HabitData.save(this, r);
            toast(on ? (Strings.en() ? "Morning check-in on: the morning page comes first each day"
                            : "晨间首填已打开：每天先过晨间页，填完才开启今天")
                    : (Strings.en() ? "Morning check-in off" : "晨间首填已关闭"));
            buildUi();
        });
        row(Strings.rowMorning(), Strings.rowMorningSub(), ms, null);
        final int ds = HabitData.morningDayStartMinutes(hroot);
        row(Strings.rowDayStart(), Strings.en() ? "The first open after this time counts as a new day"
                        : "这个时间之后第一次打开，算新的一天",
                arrowBtn(dayStartText(ds) + " ›"), v -> pickDayStart(ds));
        TextView note = new TextView(this);
        note.setText(Strings.en()
                ? "When on: after that time, the first open of KnowDo each day goes to the morning page — "
                + "yesterday's habit check-in, yesterday's mini report, and a morning diary line — "
                + "and the day only starts once all three are filled."
                : "开启后，每天这个时间之后第一次打开知行，先进入晨间页：昨日习惯打卡＋昨日小报＋晨间日记，填完才开启今天。");
        note.setTextSize(12);
        note.setTextColor(Ui.sub(this));
        note.setPadding(0, dp(6), 0, dp(2));
        root.addView(note);
    }

    static String dayStartText(int mins) {
        int h = mins / 60, m = mins % 60;
        if (Strings.en()) return String.format(java.util.Locale.US, "%d:%02d", h, m);
        return (h < 5 ? "凌晨 " : "早上 ") + h + ":" + (m == 0 ? "00" : String.valueOf(m));
    }

    private void pickDayStart(int cur) {
        final int[] opts = new int[13];
        String[] labels = new String[13];
        int sel = 0;
        for (int i = 0; i < 13; i++) {
            opts[i] = 120 + i * 30;
            labels[i] = dayStartText(opts[i]);
            if (opts[i] == cur) sel = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(Strings.rowDayStart())
                .setSingleChoiceItems(labels, sel, (d, which) -> {
                    org.json.JSONObject r = HabitData.load(this);
                    HabitData.setMorning(r, HabitData.morningEnabled(r), opts[which]);
                    HabitData.save(this, r);
                    d.dismiss();
                    buildUi();
                })
                .setNegativeButton(Strings.en() ? "Cancel" : "取消", null)
                .show();
    }

    // ---------------- 我的志（v1.12.0） ----------------

    /** 立志常驻：用户亲手写的一句话，展示在守卫问询页与各检查页。设置只给一句轻提示。 */
    private void buildAspirationGroup() {
        groupTitle(Strings.grpAspiration());
        String cur = Aspiration.get(this);
        row(cur.isEmpty() ? Strings.aspEmpty() : cur, Strings.aspRowHint(), arrowBtn("›"),
                v -> editAspiration());
    }

    private void editAspiration() {
        final EditText input = new EditText(this);
        input.setText(Aspiration.get(this));
        input.setHint(Strings.aspEditHint());
        input.setMinLines(3);
        input.setGravity(Gravity.TOP);
        input.setTextSize(17);
        input.setTextColor(Ui.text(this));
        input.setHintTextColor(Ui.sub(this));
        new AlertDialog.Builder(this)
                .setTitle(Strings.aspEditTitle())
                .setView(input)
                .setPositiveButton(Strings.en() ? "Save" : "保存", (d, w) -> {
                    Aspiration.setLocal(this, input.getText().toString());
                    toast(Strings.aspSaved());
                    buildUi();
                    // 回写云端：后台同步一次（未登录/桥模式时同步自己会短路，不打扰）
                    AccountUi.runSync(this, store, this::buildUi, false);
                })
                .setNegativeButton(Strings.en() ? "Cancel" : "取消", null)
                .show();
    }

    private void groupTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(Ui.accent(this));
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setPadding(0, dp(20), 0, dp(4));
        root.addView(t);
    }

    /** 一行设置：左标题（＋可选小字说明），右可选控件；整行可点（onClick，可为 null）。 */
    private LinearLayout row(String label, String sub, View control, View.OnClickListener onClick) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(9), 0, dp(9));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(15);
        l.setTextColor(Ui.text(this));
        texts.addView(l);
        if (sub != null && !sub.isEmpty()) {
            TextView s2 = new TextView(this);
            s2.setText(sub);
            s2.setTextSize(12);
            s2.setTextColor(Ui.sub(this));
            texts.addView(s2);
        }
        r.addView(texts);
        if (control != null) r.addView(control);
        if (onClick != null) r.setOnClickListener(onClick);
        View line = new View(this);
        line.setBackgroundColor(Ui.line(this));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        root.addView(r);
        root.addView(line, llp);
        return r;
    }

    private TextView arrowBtn(String s) {
        TextView b = new TextView(this);
        b.setText(s);
        b.setTextSize(14);
        b.setTextColor(Ui.link(this));
        b.setPadding(dp(10), dp(4), dp(2), dp(4));
        return b;
    }

    private Switch mkSwitch(boolean on) {
        Switch sw = new Switch(this);
        sw.setChecked(on);
        return sw;
    }

    // ---------------- 账号组 ----------------

    /** 账号区状态只留一行简洁文本（v1.9.0 按用户定案）：不露桥文件原始串与同步明细。 */
    private String accountStatusText() {
        if (Supa.isLoggedIn(this)) {
            if (Strings.en()) {
                int n = SbSync.pendingCount(this, store);
                return Supa.userEmail(this) + (SbSync.isSyncing() ? " · syncing…"
                        : n > 0 ? " · " + n + " pending" : " · synced");
            }
            return SbSync.statusText(this, store);
        }
        return Strings.bridgeModeLabel();
    }

    private void buildAccountGroup() {
        groupTitle(Strings.grpAccount());
        row(Supa.isLoggedIn(this) ? Supa.userEmail(this) : Strings.notLoggedIn(),
                accountStatusText(), null, null);
        row(Strings.rowAccount(), null, arrowBtn("›"),
                v -> AccountUi.show(this, store, this::buildUi));
        row(Strings.rowSyncNow(), null, arrowBtn("›"),
                v -> AccountUi.runSync(this, store, this::buildUi, true));
        // 「实时同步」开关已按用户定案去掉（v1.9.0）：云端模式实时推送常开，
        // 桥模式维持原 30 秒轮询，常开语义收进 SyncManager.realtimeEnabled。
        Uri uri = SyncManager.getUri(this);
        row(Strings.rowBridgeFile(), uri == null ? Strings.rowBridgeNone()
                        : Strings.rowBridgeSelected(),
                arrowBtn(Strings.rowBridgePick()), v -> pickBridgeFile());
    }

    private void pickBridgeFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_PICK_BRIDGE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_BRIDGE || resultCode != RESULT_OK || data == null
                || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) { }
        SyncManager.setUri(this, uri);
        toast((Strings.en() ? "Bridge file: " : "已选桥文件：") + bridgeName(uri));
        if (SyncManager.realtimeEnabled(this)) SyncService.start(this);
        AccountUi.runSync(this, store, this::buildUi, true);
    }

    // ---------------- 显示组 ----------------

    /** 可显隐的列：id 与 MainActivity 的列定义一致，共用 Ui 的同一份本地存储。 */
    static final String[][] TOGGLE_COLS = {
            {"start", null}, {"used", null}, {"left", null}, {"dur", null},
            {"dl", null}, {"pri", null}, {"sc", null},
    };

    static String colLabel(String id) {
        switch (id) {
            case "start": return Strings.colStart();
            case "used": return Strings.colUsed();
            case "left": return Strings.colLeft();
            case "dur": return Strings.colDur();
            case "dl": return Strings.colDeadline();
            case "pri": return Strings.colPriority();
            case "sc": return Strings.colScenarios();
            default: return id;
        }
    }

    static boolean colDefVisible(String id) { return !"used".equals(id); }

    private void dialogColumns() {
        final String[] ids = {"start", "used", "left", "dur", "dl", "pri", "sc"};
        String[] labels = new String[ids.length];
        boolean[] checked = new boolean[ids.length];
        for (int i = 0; i < ids.length; i++) {
            labels[i] = colLabel(ids[i]);
            checked[i] = Ui.colVisible(this, ids[i], colDefVisible(ids[i]));
        }
        new AlertDialog.Builder(this)
                .setTitle(Strings.columnsTitle())
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) ->
                        Ui.setColVisible(this, ids[which], isChecked))
                .setPositiveButton(Strings.en() ? "Done" : "完成", null)
                .show();
    }

    private String themeName() {
        String p = Ui.themePref(this);
        if ("light".equals(p)) return Strings.themeLight();
        if ("dark".equals(p)) return Strings.themeDark();
        return Strings.themeFollow();
    }

    private String langName() {
        String p = Ui.langPref(this);
        if ("zh".equals(p)) return Strings.langZh();
        if ("en".equals(p)) return Strings.langEn();
        return Strings.langFollow();
    }

    private void buildDisplayGroup() {
        groupTitle(Strings.grpDisplay());
        row(Strings.rowColumns(), null, arrowBtn("›"), v -> dialogColumns());
        row(Strings.rowAppearance(), themeName(), arrowBtn("›"), v -> {
            String[] items = {Strings.themeFollow(), Strings.themeLight(), Strings.themeDark()};
            String[] vals = {"auto", "light", "dark"};
            int cur = "light".equals(Ui.themePref(this)) ? 1
                    : "dark".equals(Ui.themePref(this)) ? 2 : 0;
            new AlertDialog.Builder(this)
                    .setTitle(Strings.rowAppearance())
                    .setSingleChoiceItems(items, cur, (d, which) -> {
                        Ui.setThemePref(this, vals[which]);
                        d.dismiss();
                        recreate();
                    })
                    .setNegativeButton(Strings.en() ? "Cancel" : "取消", null)
                    .show();
        });
        row(Strings.rowLanguage(), langName(), arrowBtn("›"), v -> {
            String[] items = {Strings.langFollow(), Strings.langZh(), Strings.langEn()};
            String[] vals = {"auto", "zh", "en"};
            int cur = "zh".equals(Ui.langPref(this)) ? 1
                    : "en".equals(Ui.langPref(this)) ? 2 : 0;
            new AlertDialog.Builder(this)
                    .setTitle(Strings.rowLanguage())
                    .setSingleChoiceItems(items, cur, (d, which) -> {
                        Ui.setLangPref(this, vals[which]);
                        d.dismiss();
                        recreate();
                    })
                    .setNegativeButton(Strings.en() ? "Cancel" : "取消", null)
                    .show();
        });
    }

    // ---------------- 守卫组 ----------------

    private void buildGuardsGroup() {
        groupTitle(Strings.grpGuards());
        boolean ready = FocusGuard.detectorReady(this);
        row(Strings.rowFocusGuard(),
                Strings.rowFocusGuardSub() + "\n"
                        + (ready ? Strings.focusReady() : Strings.focusNotReady()),
                arrowBtn("›"), v -> startActivity(new Intent(this, PermActivity.class)));
        Switch sg = mkSwitch(ScreenGuardPrefs.enabled(this));
        sg.setOnCheckedChangeListener((b, on) -> {
            ScreenGuardPrefs.setEnabled(this, on);
            if (on && TimerState.isRunning(this)) ScreenCaptureService.onTimerStarted(this);
            if (!on) ScreenCaptureService.onTimerStopped(this);
            toast(on ? (Strings.en() ? "Screen content guard on" : "屏幕内容守卫已打开：下次开始计时时会请你授权一次截屏")
                    : (Strings.en() ? "Screen content guard off" : "屏幕内容守卫已关闭"));
        });
        row(Strings.rowScreenGuard(), Strings.en() ? "Details & capture grant live in Permissions"
                : "细项与截屏授权在权限页", sg, null);
        Switch ns = mkSwitch(ScreenGuardPrefs.nsfwOneShot(this));
        ns.setOnCheckedChangeListener((b, on) -> {
            ScreenGuardPrefs.setNsfwOneShot(this, on);
            toast(on ? (Strings.en() ? "NSFW now triggers on the first hit" : "黄色类改为一张即触发")
                    : (Strings.en() ? "NSFW back to two confirmations" : "黄色类恢复两次确认"));
        });
        row(Strings.rowNsfwOneShot(), null, ns, null);
        // 任务衔接守卫（v1.10.0）：只存本地（IdleGuard prefs），不进同步；关掉时看门狗整体停用
        Switch ng = mkSwitch(IdleGuard.enabled(this));
        ng.setOnCheckedChangeListener((b, on) -> {
            IdleGuard.setEnabled(this, on);
            toast(on ? (Strings.en() ? "Next-task guard on" : "任务衔接守卫已打开：空档时会全屏问下一个任务")
                    : (Strings.en() ? "Next-task guard off" : "任务衔接守卫已关闭：空档时不再自动弹出（暂停规则不受影响）"));
        });
        row(Strings.rowNextGuard(), Strings.rowNextGuardSub(), ng, null);
        buildIdleNagRows();
        row(Strings.rowPerms(), null, arrowBtn("›"),
                v -> startActivity(new Intent(this, PermActivity.class)));
    }

    // ---------------- 空档催促（v1.14.0）：个人设置，存本地＋云端 user_state.idle_nag 同步 ----------------

    private void buildIdleNagRows() {
        boolean en = Strings.en();
        groupTitle(en ? "Idle nagging" : "空档催促");
        Switch master = mkSwitch(IdleNag.enabled(this));
        master.setOnCheckedChangeListener((b, on) -> {
            IdleNag.setEnabled(this, on);
            afterNagChanged();
        });
        row(en ? "Nag me when idle" : "空档时催我",
                en ? "Your settings, synced via cloud — phone and computer share them"
                        : "你的个人设置，存云端同步，手机与电脑共用一份",
                master, null);
        final String p = IdleNag.profile(this);
        row(en ? "Intensity" : "强度档位", IdleNag.rhythmText(this),
                arrowBtn(IdleNag.profileName(p) + " ›"), v -> pickProfile());
        if (IdleNag.GENTLE.equals(p)) return; // 温和＝什么都不发生，没有可调项
        row(en ? "Minutes idle before nagging" : "几分钟没任务算空档",
                null, arrowBtn(IdleNag.idleMinutes(this) + (en ? " min" : " 分钟")),
                v -> editNagNumber("idle_minutes", en ? "Minutes idle before nagging" : "几分钟没任务算空档",
                        IdleNag.idleMinutes(this), 1, 120, en ? " min" : " 分钟"));
        if (IdleNag.STRICT.equals(p) || IdleNag.CUSTOM.equals(p)) {
            row(en ? "Seconds each round rings" : "每轮响多久",
                    null, arrowBtn(IdleNag.ringSeconds(this) + (en ? " sec" : " 秒")),
                    v -> editNagNumber("ring_seconds", en ? "Seconds each round rings" : "每轮响多久（秒）",
                            IdleNag.ringSeconds(this), 5, 600, en ? " sec" : " 秒"));
        }
        row(en ? "Gap after the first round" : "第一轮后隔几分钟再来",
                null, arrowBtn(IdleNag.firstRepeatMinutes(this) + (en ? " min" : " 分钟")),
                v -> editNagNumber("first_repeat_minutes", en ? "Gap after the first round" : "第一轮后隔几分钟",
                        IdleNag.firstRepeatMinutes(this), 1, 120, en ? " min" : " 分钟"));
        row(en ? "Gap between later rounds" : "之后每隔几分钟一轮",
                null, arrowBtn(IdleNag.repeatMinutes(this) + (en ? " min" : " 分钟")),
                v -> editNagNumber("repeat_minutes", en ? "Gap between later rounds" : "之后每隔几分钟",
                        IdleNag.repeatMinutes(this), 1, 120, en ? " min" : " 分钟"));
        if (IdleNag.STANDARD.equals(p)) {
            row(en ? "Max rounds" : "最多催几轮",
                    en ? "Then it stays quiet until a new idle gap" : "到上限就安静，直到下一次空档",
                    arrowBtn(String.valueOf(IdleNag.maxRounds(this))),
                    v -> editNagNumber("max_rounds", en ? "Max rounds" : "最多催几轮",
                            IdleNag.maxRounds(this), 1, 20, ""));
        }
        if (IdleNag.CUSTOM.equals(p)) {
            int mr = IdleNag.maxRounds(this);
            String strat = mr == 0 ? (en ? "Don't nag" : "不催")
                    : mr < 0 ? (en ? "Until I start a task" : "无限，直到开始任务")
                    : (en ? "At most " + mr + " rounds" : "最多 " + mr + " 轮");
            row(en ? "Repeat strategy" : "重复策略", null, arrowBtn(strat + " ›"), v -> pickStrategy());
            if (mr > 0) {
                row(en ? "Max rounds" : "最多催几轮", null,
                        arrowBtn(String.valueOf(mr)),
                        v -> editNagNumber("max_rounds", en ? "Max rounds" : "最多催几轮", mr, 1, 20, ""));
            }
        }
        // 声音只属严格与自定义；标准档只注明没有声音
        if (IdleNag.STRICT.equals(p) || IdleNag.CUSTOM.equals(p)) {
            Switch snd = mkSwitch(IdleNag.sound(this));
            snd.setOnCheckedChangeListener((b, on) -> {
                IdleNag.setSound(this, on);
                afterNagChanged();
            });
            row(en ? "Sound" : "出声", en ? "Birdsong alarm, fades in" : "鸟鸣渐强，走闹钟通道", snd, null);
        } else if (IdleNag.STANDARD.equals(p)) {
            row(en ? "Sound" : "声音", en ? "The standard level has no sound — popup only"
                    : "标准档没有声音，只弹页面", null, null);
        }
    }

    /** 设置改完：行为层按新参数重排当前周期，页面重画，并后台同步一次回写云端。 */
    private void afterNagChanged() {
        IdleAlarm.reevaluate(this);
        buildUi();
        AccountUi.runSync(this, store, this::buildUi, false);
    }

    private void pickProfile() {
        boolean en = Strings.en();
        String[] items = en
                ? new String[]{"Gentle — a task ends, and that's it. No nagging.",
                        "Standard — reminds you, but at most a few rounds. No sound.",
                        "Strict — keeps reminding (with sound) until you start a task.",
                        "Custom — your own strategy and numbers."}
                : new String[]{"温和 — 任务结束就结束，不催你",
                        "标准 — 催，但最多几轮就安静；不出声",
                        "严格 — 每隔几分钟出声催你，直到你开始任务",
                        "自定义 — 重复策略和每个数字自己定"};
        new AlertDialog.Builder(this)
                .setTitle(en ? "Intensity" : "强度档位")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: IdleNag.applyProfile(this, IdleNag.GENTLE); break;
                        case 1: IdleNag.applyProfile(this, IdleNag.STANDARD); break;
                        case 2: IdleNag.applyProfile(this, IdleNag.STRICT); break;
                        default: IdleNag.enterCustom(this); break;
                    }
                    afterNagChanged();
                })
                .setNegativeButton(en ? "Cancel" : "取消", null)
                .show();
    }

    private void pickStrategy() {
        boolean en = Strings.en();
        String[] items = en
                ? new String[]{"Don't nag", "Limited rounds", "Until I start a task"}
                : new String[]{"不催", "有限轮数", "无限，直到开始任务"};
        new AlertDialog.Builder(this)
                .setTitle(en ? "Repeat strategy" : "重复策略")
                .setItems(items, (d, which) -> {
                    if (which == 0) IdleNag.setCustomMaxRounds(this, 0);
                    else if (which == 2) IdleNag.setCustomMaxRounds(this, -1);
                    else {
                        int mr = IdleNag.maxRounds(this);
                        IdleNag.setCustomMaxRounds(this, mr > 0 ? mr : 3);
                    }
                    afterNagChanged();
                })
                .setNegativeButton(en ? "Cancel" : "取消", null)
                .show();
    }

    private void editNagNumber(final String key, String title, int current,
                               final int min, final int max, String unit) {
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(current));
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setPositiveButton(Strings.en() ? "Save" : "保存", (d, w) -> {
                    int val = current;
                    try { val = Integer.parseInt(input.getText().toString().trim()); } catch (Exception ignored) { }
                    val = Math.max(min, Math.min(max, val));
                    IdleNag.editParam(this, key, val);
                    afterNagChanged();
                })
                .setNegativeButton(Strings.en() ? "Cancel" : "取消", null)
                .show();
    }

    // ---------------- 通用组 ----------------

    private void buildGeneralGroup() {
        groupTitle(Strings.grpGeneral());
        Switch fw = mkSwitch(FloatingWindow.enabled(this));
        fw.setOnCheckedChangeListener((b, on) -> {
            FloatingWindow.setEnabled(this, on);
            if (on) FloatService.start(this);
            toast(on ? (Strings.en() ? "Floating window on" : "悬浮窗已打开")
                    : (Strings.en() ? "Floating window off" : "悬浮窗已关闭"));
        });
        row(Strings.rowFloatWin(), null, fw, null);
        row(Strings.rowPerms(), null, arrowBtn("›"),
                v -> startActivity(new Intent(this, PermActivity.class)));
        row(Strings.rowLog(), null, arrowBtn("›"),
                v -> startActivity(new Intent(this, LogActivity.class)));
        row(Strings.help(), null, arrowBtn("›"),
                v -> startActivity(new Intent(this, HelpActivity.class)));
        row(Strings.whatsNewEntry(), null, arrowBtn("›"),
                v -> WhatsNew.show(this));
        row(Strings.rowAbout(), Strings.versionLine(), null, v ->
                new AlertDialog.Builder(this)
                        .setTitle(Strings.appFull())
                        .setMessage(Strings.aboutBody())
                        .setPositiveButton(Strings.en() ? "OK" : "好", null)
                        .show());
    }
}
