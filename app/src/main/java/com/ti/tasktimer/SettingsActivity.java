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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 设置页（v1.8.0 新设）：按「账号／显示／守卫／通用」四组承接原主页顶栏、权限页的全部设置与入口。
 * 红线：只是搬位置——开关都写回各自原有的存储（SyncManager / ScreenGuardPrefs / FloatingWindow /
 * Ui 本地偏好），同步与守卫语义一律不变。
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

        buildAccountGroup();
        buildDisplayGroup();
        buildGuardsGroup();
        buildGeneralGroup();
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

    private String accountStatusText() {
        if (Supa.isLoggedIn(this)) {
            if (Strings.en()) {
                int n = SbSync.pendingCount(this, store);
                return Supa.userEmail(this) + (SbSync.isSyncing() ? " · syncing…"
                        : n > 0 ? " · " + n + " pending" : " · synced");
            }
            return SbSync.statusText(this, store);
        }
        Uri uri = SyncManager.getUri(this);
        if (Strings.en()) {
            return Strings.bridgeModeLabel() + " · "
                    + (uri == null ? Strings.rowBridgeNone() : bridgeName(uri));
        }
        return "Drive 桥：" + (uri == null ? "还没选桥文件" : bridgeName(uri))
                + " · " + SyncManager.lastResult(this);
    }

    private void buildAccountGroup() {
        groupTitle(Strings.grpAccount());
        row(Supa.isLoggedIn(this) ? Supa.userEmail(this) : Strings.notLoggedIn(),
                accountStatusText(), null, null);
        row(Strings.rowAccount(), null, arrowBtn("›"),
                v -> AccountUi.show(this, store, this::buildUi));
        row(Strings.rowSyncNow(), null, arrowBtn("›"),
                v -> AccountUi.runSync(this, store, this::buildUi, true));
        Switch rt = mkSwitch(SyncManager.realtimeEnabled(this));
        rt.setOnCheckedChangeListener((b, on) -> toggleRealtime(on));
        row(Strings.rowRealtime(), Strings.en() ? "Bridge: ~30s poll · Cloud: realtime push"
                : "桥模式约 30 秒轮询 · 云端模式实时推送", rt, null);
        Uri uri = SyncManager.getUri(this);
        row(Strings.rowBridgeFile(), uri == null ? Strings.rowBridgeNone()
                        : bridgeName(uri),
                arrowBtn(Strings.rowBridgePick()), v -> pickBridgeFile());
    }

    private void toggleRealtime(boolean on) {
        SyncManager.setRealtimeEnabled(this, on);
        if (on) {
            if (SyncManager.isSupabaseMode(this)) {
                SyncService.start(this);
                toast(Strings.en() ? "Realtime on (cloud)" : "实时已开（云端推送）");
            } else if (SyncManager.getUri(this) != null) {
                SyncService.start(this);
                AccountUi.runSync(this, store, this::buildUi, false);
                toast(Strings.en() ? "Realtime on: syncs about every 30 seconds"
                        : "实时已开：约 30 秒自动同步一次");
            } else {
                toast(Strings.en() ? "Realtime on: pick the bridge file or sign in first"
                        : "实时已开：请先选桥文件（本页账号组）或登录云端账号");
            }
        } else {
            SyncService.stop(this);
            toast(Strings.en() ? "Realtime off" : "实时已关：只在打开 App 时同步");
        }
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
        row(Strings.rowPerms(), null, arrowBtn("›"),
                v -> startActivity(new Intent(this, PermActivity.class)));
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
        row(Strings.rowAbout(), Strings.versionLine(), null, v ->
                new AlertDialog.Builder(this)
                        .setTitle(Strings.appFull())
                        .setMessage(Strings.aboutBody())
                        .setPositiveButton(Strings.en() ? "OK" : "好", null)
                        .show());
    }
}
