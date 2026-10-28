package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
 * v1.9.0：账号区状态精简为一行、「实时同步」开关整行去掉（云端实时推送常开）。
 * v1.26.0：Drive 桥整套移除——桥文件行、选文件流程与桥状态文案一并删去，
 * 账号组只剩云端账号与立即同步。
 */
public class SettingsActivity extends Activity {
    private TaskStore store;
    private LinearLayout root;

    // 软件更新状态回调（v1.27.0）：下载进度/状态变化时重画本页，让更新行的小字跟上
    private final AppUpdate.Listener updateListener = () -> {
        if (!isFinishing()) buildUi();
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        store = new TaskStore(this);
        AppUpdate.setListener(updateListener);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        store.load();
        AppUpdate.setListener(updateListener);
        buildUi(); // 回到本页时状态（登录/开关/桥文件）可能已变，整页重画最稳
    }

    @Override protected void onDestroy() {
        AppUpdate.setListener(null);
        super.onDestroy();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

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
        buildUpdateGroup();
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
        // 测试按钮（v1.17.0）：白天也能把正式晨间页完整试一遍（含暂停推后）
        row(Strings.en() ? "Test the morning page" : "测试晨间页",
                Strings.en() ? "Opens the real morning page — finishing it counts as today's check-in"
                        : "打开的是正式晨间页，填完即算完成今天的晨间首填",
                arrowBtn(Strings.en() ? "Open ›" : "打开 ›"),
                v -> MorningGate.launchTest(this));
        TextView note = new TextView(this);
        note.setText(Strings.en()
                ? "When on: after that time, the first time you pick up your phone each day (unlock it) "
                + "the morning page comes up full-screen on its own — yesterday's habit check-in, "
                + "yesterday's mini report, and a morning diary line — and the day only starts once "
                + "all three are filled. Opening KnowDo first works too. Something urgent? "
                + "The page's \"Pause for a bit\" steps it aside and brings it back later."
                : "开启后，每天这个时间之后，你早上第一次拿起手机（解锁）晨间页就会全屏出现，不必先打开知行；"
                + "先打开知行也一样会进。内容＝昨日习惯打卡＋昨日小报＋晨间日记，填完才开启今天。"
                + "有急事可以点页里的「暂停一会儿」先推后，到点它会自己回来。");
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
        return Strings.en() ? "Not signed in — tap Account to sign in"
                : "未登录，点「账号」登录后走云端同步";
    }

    private void buildAccountGroup() {
        groupTitle(Strings.grpAccount());
        row(Supa.isLoggedIn(this) ? Supa.userEmail(this) : Strings.notLoggedIn(),
                accountStatusText(), null, null);
        row(Strings.rowAccount(), null, arrowBtn("›"),
                v -> AccountUi.show(this, store, this::buildUi));
        row(Strings.rowSyncNow(), null, arrowBtn("›"),
                v -> AccountUi.runSync(this, store, this::buildUi, true));
        // 「实时同步」开关已按用户定案去掉（v1.9.0）：云端实时推送常开，
        // 常开语义收进 SyncManager.realtimeEnabled。
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

    // ---------------- 软件更新组（v1.27.0）：软件自己查新版、自己下、下好点一下装 ----------------
    // 通道与契约见 AppUpdate；下载网络经云端 user_state.update_prefs 同步（UpdatePrefs）。

    private void buildUpdateGroup() {
        final boolean en = Strings.en();
        groupTitle(en ? "App Updates" : "软件更新");
        row(en ? "Current version" : "当前版本", Strings.versionLine(), null, null);
        Switch ac = mkSwitch(UpdatePrefs.autoCheck(this));
        ac.setOnCheckedChangeListener((b, on) -> {
            UpdatePrefs.setAutoCheck(this, on);
            toast(on ? (en ? "Auto-check on: KnowDo checks for a new version at most once a day"
                            : "自动检查已打开：每天至多自动查一次有没有新版")
                    : (en ? "Auto-check off" : "自动检查已关闭"));
        });
        row(en ? "Check for updates automatically" : "自动检查更新",
                en ? "When a new version appears it downloads quietly in the background; "
                        + "you only tap Install once"
                        : "有新版时在后台安静下载，下好后你点一下「安装」就行",
                ac, null);
        row(en ? "Download over" : "下载网络",
                en ? "Which network may be used to download a new version automatically. "
                        + "Your pick, synced via cloud"
                        : "新版自动下载时可以用哪种网络。你的个人设置，存云端同步",
                arrowBtn(UpdatePrefs.wifiOnlyLabel(UpdatePrefs.wifiOnly(this)) + " ›"),
                v -> pickWifiOnly());
        // 检查更新行：副标题就是实时状态（下载进度也在这里显示，不打扰别处）；
        // 已下好待安装时整行点击直接走安装。
        boolean ready = AppUpdate.pendingReady(this);
        row(en ? "Check for updates" : "检查更新",
                AppUpdate.statusText(this),
                arrowBtn(ready ? (en ? "Install ›" : "安装 ›") : "›"),
                v -> AppUpdate.onSettingsRowTapped(this));
    }

    /** 下载网络二选一（v1.27.0）：写本地＋回写云端在 UpdatePrefs.setWifiOnly 里。 */
    private void pickWifiOnly() {
        final boolean en = Strings.en();
        final boolean[] vals = {true, false};
        String[] items = {UpdatePrefs.wifiOnlyLabel(true), UpdatePrefs.wifiOnlyLabel(false)};
        int cur = UpdatePrefs.wifiOnly(this) ? 0 : 1;
        new android.app.AlertDialog.Builder(this)
                .setTitle(en ? "Download over" : "下载网络")
                .setSingleChoiceItems(items, cur, (d, which) -> {
                    UpdatePrefs.setWifiOnly(this, vals[which]);
                    d.dismiss();
                    toast(en ? "New versions will download over: " + items[which]
                            : "以后新版下载网络：" + items[which]);
                    buildUi();
                })
                .setNegativeButton(en ? "Cancel" : "取消", null)
                .show();
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
        row(Strings.en() ? "Habit display window" : "习惯显示口径",
                Strings.en() ? "Which window the counts on habit cards use. Your pick, synced via cloud"
                        : "习惯页卡片上的次数按哪个时段来数。你的个人设置，存云端同步",
                arrowBtn(HabitDisplay.label(HabitDisplay.scope(this)) + " ›"),
                v -> pickHabitDisplay());
    }


    /** 习惯显示口径四选一（v1.28.0）：写本地＋回写云端在 HabitDisplay.setScope 里。 */
    private void pickHabitDisplay() {
        final String[] vals = {HabitDisplay.SCOPE_TODAY, HabitDisplay.SCOPE_WEEK,
                HabitDisplay.SCOPE_LAST_WEEK, HabitDisplay.SCOPE_MONTH};
        String[] items = new String[vals.length];
        int cur = 1;
        for (int i = 0; i < vals.length; i++) {
            items[i] = HabitDisplay.label(vals[i]);
            if (vals[i].equals(HabitDisplay.scope(this))) cur = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(Strings.en() ? "Habit display window" : "习惯显示口径")
                .setSingleChoiceItems(items, cur, (d, which) -> {
                    HabitDisplay.setScope(this, vals[which]);
                    d.dismiss();
                    toast(Strings.en() ? "Habit cards now count: " + items[which]
                            : "习惯卡片改按「" + items[which] + "」口径显示");
                    buildUi();
                })
                .setNegativeButton(Strings.en() ? "Cancel" : "取消", null)
                .show();
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
            if (on) {
                // v1.21.4：模型按需下载后，开守卫先过闸门——模型没下好先弹下载引导，不直接开
                GuardEnableGate.tryEnable(this, () -> toast(Strings.en()
                        ? "Screen content guard on: screenshots come only from the accessibility channel"
                        : "屏幕内容守卫已打开：截图只走无障碍通道"));
            } else {
                ScreenGuardPrefs.setEnabled(this, false);
                KnowDoAccessibilityService.onTimerStopped(this);
                toast(Strings.en() ? "Screen content guard off" : "屏幕内容守卫已关闭");
            }
            buildUi();
        });
        row(Strings.rowScreenGuard(), Strings.en() ? "Details live in Permissions"
                : "细项在权限页里", sg, null);
        Switch ns = mkSwitch(ScreenGuardPrefs.nsfwOneShot(this));
        ns.setOnCheckedChangeListener((b, on) -> {
            ScreenGuardPrefs.setNsfwOneShot(this, on);
            toast(on ? (Strings.en() ? "NSFW now triggers on the first hit" : "黄色类改为一张即触发")
                    : (Strings.en() ? "NSFW back to two confirmations" : "黄色类恢复两次确认"));
        });
        row(Strings.rowNsfwOneShot(), null, ns, null);
        // 语义判定（v1.21.4）：粗判拿不准时先本地预筛、再云端终判；关掉只本地判、数据不出手机
        Switch sem = mkSwitch(ScreenGuardPrefs.semanticEnabled(this));
        sem.setOnCheckedChangeListener((b, on) -> {
            ScreenGuardPrefs.setSemanticEnabled(this, on);
            toast(on ? (Strings.en() ? "Semantic verdicts on" : "语义判定已打开")
                    : (Strings.en() ? "Semantic verdicts off: on-device judging only, nothing leaves the phone"
                    : "语义判定已关闭：只用手机本地判定，任何数据都不出手机"));
        });
        row(Strings.en() ? "Semantic verdicts" : "语义判定",
                Strings.en() ? "When the guard can't decide, it compares the screen text with your task — "
                        + "on-device first, and only if still unsure, the masked text (never an image) goes "
                        + "to a cloud model for the final call. Turn it off and judging stays fully on-device"
                        : "守卫拿不准时把屏幕文字和任务比一比：先用手机本地的小模型比，还拿不准才把文字"
                        + "（敏感信息已自动打码，绝不发图）发给云端大模型帮判。关掉则只用手机本地判定，任何数据都不出手机",
                sem, null);
        // 我的纪律（v1.24.3）：守卫问询页点「分析错误」留下的纪律，在这里查看与删除
        row(Strings.en() ? "My guard rules" : "我的纪律",
                Strings.en() ? "Rules you left after tapping \"Analysis error\" on a guard inquiry; "
                        + "online verdicts judge with them included"
                        : "守卫判错时你留下的纪律，联网判定会带着它一起判；在这里查看、删除",
                arrowBtn("›"), v -> startActivity(new Intent(this, GuardRulesActivity.class)));
        // 任务软件名单（v1.26.0）：关了不发名单生成请求、不按名单弹问询，兜底名单也停
        Switch al = mkSwitch(ScreenGuardPrefs.appListsEnabled(this));
        al.setOnCheckedChangeListener((b, on) -> {
            ScreenGuardPrefs.setAppListsEnabled(this, on);
            toast(on ? (Strings.en() ? "App lists on" : "任务软件名单已打开")
                    : (Strings.en() ? "App lists off: no requests, no list-based inquiries"
                    : "任务软件名单已关闭：不再发名单请求，也不再按名单弹问询"));
        });
        row(Strings.en() ? "Per-task app lists" : "任务软件名单",
                Strings.en() ? "When a task starts, your frequently used apps are sorted into "
                        + "white / grey / black for that task in the background. Opening a blacklisted "
                        + "app during the task asks right away; whitelisted apps never trigger focus-rule inquiries"
                        : "开始任务时，常用软件会被按这个任务分成白、灰、黑三档（在后台生成）。计时中打开"
                        + "黑名单软件立刻弹问询，白名单软件不再触发专注规则问询。在任务详情里点「软件名单」查看和改档",
                al, null);
        // 提示页重弹间隔（v1.26.3）：全屏提示页被划走、还没处理完时隔多久弹回来；
        // 一个数值四处共用（晨间门重弹／守卫问询重拉／空档选任务页重拉／静音叫回），
        // 存本地＋云端 user_state.prompt_repop 同步（PromptRepop）
        row(Strings.en() ? "Prompt re-pop interval" : "提示页重弹间隔",
                Strings.en() ? "When you swipe a full-screen prompt away without finishing it "
                        + "(morning check-in, guard inquiry, next-task page, silent pause call-back), "
                        + "how long before it pops back. Your pick, synced via cloud"
                        : "全屏提示页（晨间页、守卫问询、选下一个任务、静音叫回）被划走、还没处理完时，"
                        + "隔多久再弹回来。你的个人设置，存云端同步",
                arrowBtn(PromptRepop.label(PromptRepop.seconds(this)) + " ›"), v -> pickRepop());
        // 测试者模式（内部 key diag_mode，v1.24.1 由暂名「测试模式」正式改名，用户钦定）：
        // 给愿意帮忙 debug、检查软件错误的用户用的模式，与守卫测试模式是两回事
        Switch dg = mkSwitch(ScreenGuardPrefs.diagMode(this));
        dg.setOnCheckedChangeListener((b, on) -> {
            ScreenGuardPrefs.setDiagMode(this, on);
            toast(on ? (Strings.en() ? "Tester mode on: internal errors pop up with their status codes"
                            : "测试者模式已打开：内部报错会弹悬浮窗亮出状态码")
                    : (Strings.en() ? "Tester mode off" : "测试者模式已关闭"));
        });
        row(Strings.en() ? "Tester mode" : "测试者模式",
                Strings.en() ? "For people helping debug and check the app for errors: verdicts show "
                        + "where each call came from, and internal errors pop up in a small overlay "
                        + "with their HTTP status and reason for about five seconds"
                        : "打开后，判定结果会显示来源等诊断信息；软件内部报错还会额外弹一个"
                        + "悬浮窗（约 5 秒），把状态码和原因亮出来，方便帮忙查错",
                dg, null);
        row(Strings.en() ? "Model downloads" : "模型下载",
                Strings.en() ? "Guard models are downloaded on demand, not packed into the app"
                        : "守卫用的模型按需下载，不再装进安装包",
                arrowBtn("›"), v -> startActivity(new Intent(this, ModelActivity.class)));
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
        // 每轮先震动多久（v1.19.0 staging）：标准/严格/自定义都有震动段，都可调（0＝不震动）
        row(en ? "Seconds each round vibrates first" : "每轮先震动多久",
                en ? "Vibration comes before any sound — a silent first step"
                        : "每轮先震动不出声，无反应才进入声音段",
                arrowBtn(IdleNag.vibrateSeconds(this) + (en ? " sec" : " 秒")),
                v -> editNagNumber("vibrate_seconds", en ? "Seconds each round vibrates first" : "每轮先震动多久（秒）",
                        IdleNag.vibrateSeconds(this), 0, 300, en ? " sec" : " 秒"));
        // 注：first_repeat_minutes 字段仍在数据里保留兼容，但 v1.19.0 staging 起轮间隔
        // 统一走 repeat_minutes，该参数不再单独生效，故设置页不再给它编辑行（免得改了没反应）。
        row(en ? "Gap between rounds" : "每隔几分钟一轮",
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
        EndNag.reevaluate(this); // 结束页催促同吃这套档位（v1.21.1）：关掉/转温和立即停链
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

    /** 提示页重弹间隔四档选一（v1.26.3）：写本地＋回写云端在 PromptRepop.setSeconds 里。 */
    private void pickRepop() {
        boolean en = Strings.en();
        final int[] secs = {15, 30, 60, 300};
        String[] items = new String[secs.length];
        for (int i = 0; i < secs.length; i++) items[i] = PromptRepop.label(secs[i]);
        new AlertDialog.Builder(this)
                .setTitle(en ? "Prompt re-pop interval" : "提示页重弹间隔")
                .setItems(items, (d, which) -> {
                    PromptRepop.setSeconds(this, secs[which]);
                    toast(en ? "Prompts will pop back after " + PromptRepop.label(secs[which])
                            : "提示页划走后 " + PromptRepop.label(secs[which]) + "再弹回来");
                    buildUi();
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
        // AI 对话记录清除（v1.22.0）：对话只存本机，设置里一键全清
        row(Strings.en() ? "Clear AI chats" : "清除 AI 对话记录",
                Strings.en() ? "Chats are stored only on this phone and never uploaded; clearing removes them for good"
                        : "对话只存这台手机、从不上云；清除了就找不回来",
                arrowBtn("›"),
                v -> new AlertDialog.Builder(this)
                        .setTitle(Strings.en() ? "Clear all AI chats?" : "清除全部 AI 对话记录？")
                        .setMessage(Strings.en() ? "Every task chat and the general chat will be removed from this phone"
                                : "每个任务的对话和通用对话都会从这台手机上删掉")
                        .setPositiveButton(Strings.en() ? "Clear" : "清除", (d, w) -> {
                            ChatStore.clearAll(this);
                            toast(Strings.en() ? "AI chats cleared" : "AI 对话记录已清除");
                        })
                        .setNegativeButton(Strings.en() ? "Cancel" : "取消", null)
                        .show());
        row(Strings.help(), null, arrowBtn("›"),
                v -> startActivity(new Intent(this, HelpActivity.class)));
        row(Strings.whatsNewEntry(), null, arrowBtn("›"),
                v -> WhatsNew.show(this));
        // 建议设置（v1.19.0）：常设入口，随时复查六项系统开关（电池/悬浮窗/无障碍/全屏通知/精确闹钟/通知）
        row(Strings.en() ? "Suggested settings" : "建议设置",
                Strings.en() ? "Six system switches the guards rely on — all optional, all checkable here"
                        : "守卫们靠的六项系统开关：都是建议不是门槛，随时来这里复查",
                arrowBtn("›"),
                v -> startActivity(new Intent(this, SuggestedSettingsActivity.class)));
        row(Strings.rowAbout(), Strings.versionLine(), null, v ->
                new AlertDialog.Builder(this)
                        .setTitle(Strings.appFull())
                        .setMessage(Strings.aboutBody())
                        .setPositiveButton(Strings.en() ? "OK" : "好", null)
                        .show());
    }
}
