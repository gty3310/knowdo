package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 「软件名单」页（v1.26.0，从任务详情进入）：看当前任务的三档名单，点某个
 * 软件改它的档（白/灰/黑）。名单的生成只走一条暗线——开始任务时自动发
 * 请求、云端巡查分类后回来，本页没有生成入口；未生成时显示「生成中」并
 * 列出正在兜底的通用黑名单（同样可改档）。用户手改过的软件记 manual，
 * 之后生成的名单也不会覆盖（AppLists 合并口径）。
 * 整页可滚动（v1.25.0 问询页/纪律页的教训：名单条目多，裸布局会把靠后的
 * 条目挤出屏幕）。
 */
public class AppListActivity extends Activity {
    public static final String EXTRA_TASK_ID = "applist_task_id";
    public static final String EXTRA_TASK_TITLE = "applist_task_title";

    private String taskId;
    private String taskTitle = "";
    private LinearLayout root;

    public static void open(Activity from, String taskId, String taskTitle) {
        Intent i = new Intent(from, AppListActivity.class);
        i.putExtra(EXTRA_TASK_ID, taskId);
        i.putExtra(EXTRA_TASK_TITLE, taskTitle == null ? "" : taskTitle);
        from.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        taskId = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_TASK_ID);
        String t = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_TASK_TITLE);
        if (t != null) taskTitle = t;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 243, 236));
        int pad = dp(24);
        root.setPadding(pad, dp(40), pad, pad);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(247, 243, 236));
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        build(); // 名单可能刚同步回来，回来就重画
    }

    private void build() {
        root.removeAllViews();
        boolean en = Strings.en();

        TextView title = new TextView(this);
        title.setText(en ? "App list" : "软件名单");
        title.setTextSize(26);
        title.setTextColor(Color.rgb(31, 47, 42));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText((en ? "Task: " : "任务：") + taskTitle);
        sub.setTextSize(16);
        sub.setTextColor(Color.DKGRAY);
        sub.setPadding(0, dp(6), 0, dp(6));
        root.addView(sub);

        if (!AppLists.enabled(this)) {
            TextView off = new TextView(this);
            off.setText(en ? "The app-list feature is off (Settings → Guards). The list below is kept but not enforced"
                    : "「任务软件名单」开关已关（在设置 → 守卫里），下面这份名单暂时不生效");
            off.setTextSize(15);
            off.setTextColor(Color.rgb(160, 60, 50));
            off.setPadding(0, 0, 0, dp(10));
            root.addView(off);
        }

        // 使用情况访问没开：名单排序与守卫检测都会退化，照权限页口径给一行引导
        if (!FocusGuard.detectorReady(this)) {
            TextView hint = new TextView(this);
            hint.setText(en ? "Usage access is not granted yet, so the list is ordered by name and "
                    + "the guard cannot watch foreground apps. Turn it on in Permissions"
                    : "还没开「使用情况访问」：名单只能按名称排，守卫也看不到你在用哪个软件。去权限页开启会更准");
            hint.setTextSize(14);
            hint.setTextColor(Color.DKGRAY);
            hint.setPadding(0, 0, 0, dp(6));
            root.addView(hint);
            Button go = new Button(this);
            go.setText(en ? "Open Permissions" : "去权限页开启");
            go.setAllCaps(false);
            go.setOnClickListener(v -> startActivity(new Intent(this, PermActivity.class)));
            root.addView(go);
        }

        JSONObject e = AppLists.entry(this, taskId);
        if (e == null) {
            TextView gen = new TextView(this);
            gen.setText(en ? "This task's list is still being generated — for now a built-in "
                    + "generic blacklist stands in. Start this task once and the list arrives "
                    + "a few minutes later; apps you re-tier yourself are never overwritten"
                    : "这个任务的名单还在生成中，先用一份通用黑名单兜底。下次开始这个任务后，"
                            + "名单过几分钟就到；你自己改过档的软件，名单回来也不会覆盖");
            gen.setTextSize(15);
            gen.setTextColor(Color.DKGRAY);
            gen.setPadding(0, dp(8), 0, dp(14));
            root.addView(gen);
            // 兜底名单里已安装的软件（只黑一档，可改档）
            List<String> pkgs = new ArrayList<>();
            for (Map.Entry<String, String[]> f : AppLists.fallback().entrySet()) {
                try {
                    getPackageManager().getApplicationInfo(f.getKey(), 0);
                    pkgs.add(f.getKey());
                } catch (Exception ignored) { }
            }
            Collections.sort(pkgs, (a, b) -> AppLister.labelOf(this, a).compareTo(AppLister.labelOf(this, b)));
            groupTitle(en ? "Black — generic stand-in" : "黑名单（通用兜底）");
            if (pkgs.isEmpty()) {
                emptyLine(en ? "None of the generic-blacklist apps is installed" : "通用黑名单里的软件一个都没装");
            }
            for (String pkg : pkgs) {
                appRow(pkg, AppLists.fallback().get(pkg)[1], AppLists.TIER_BLACK);
            }
            return;
        }

        JSONObject apps = e.optJSONObject("apps");
        String gen = e.optString("generatedAt", "");
        TextView state = new TextView(this);
        state.setText(en ? "List ready" + (gen.isEmpty() ? "" : " · " + gen.replace('T', ' '))
                        + ". Tap any app to re-tier it — your changes are never overwritten"
                : "名单已生成" + (gen.isEmpty() ? "" : " · " + gen.replace('T', ' '))
                        + "。点任何软件可以改档，你改过的不会再被覆盖");
        state.setTextSize(14);
        state.setTextColor(Color.DKGRAY);
        state.setPadding(0, dp(4), 0, dp(12));
        root.addView(state);

        buildGroup(en ? "White — safe to use" : "白名单 · 放心用", apps, AppLists.TIER_WHITE, en);
        buildGroup(en ? "Grey — might be useful, no interruptions" : "灰名单 · 可能有用，先不打扰", apps, AppLists.TIER_GREY, en);
        buildGroup(en ? "Black — opening it asks right away" : "黑名单 · 一打开就问", apps, AppLists.TIER_BLACK, en);
    }

    private void buildGroup(String name, JSONObject apps, String tier, boolean en) {
        groupTitle(name);
        List<String> pkgs = new ArrayList<>();
        if (apps != null) {
            java.util.Iterator<String> it = apps.keys();
            while (it.hasNext()) {
                String pkg = it.next();
                JSONObject a = apps.optJSONObject(pkg);
                if (a != null && tier.equals(a.optString("tier", ""))) pkgs.add(pkg);
            }
        }
        if (pkgs.isEmpty()) {
            emptyLine(en ? "None" : "无");
            return;
        }
        Collections.sort(pkgs, (a, b) -> AppLister.labelOf(this, a).compareTo(AppLister.labelOf(this, b)));
        for (String pkg : pkgs) {
            JSONObject a = apps.optJSONObject(pkg);
            appRow(pkg, a == null ? "" : a.optString("why", ""), tier);
        }
    }

    private void groupTitle(String name) {
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextSize(19);
        t.setTextColor(Color.rgb(31, 47, 42));
        t.setPadding(0, dp(18), 0, dp(6));
        root.addView(t);
    }

    private void emptyLine(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTextColor(Color.GRAY);
        root.addView(t);
    }

    private void appRow(final String pkg, String why, final String tier) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, dp(8));
        root.addView(row);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView name = new TextView(this);
        name.setText(AppLister.labelOf(this, pkg));
        name.setTextSize(18);
        name.setTextColor(Color.rgb(31, 47, 42));
        col.addView(name);
        if (why != null && !why.isEmpty()) {
            TextView w = new TextView(this);
            w.setText(why);
            w.setTextSize(13);
            w.setTextColor(Color.GRAY);
            col.addView(w);
        }
        row.addView(col);

        Button b = new Button(this);
        b.setText(tierName(tier));
        b.setTextSize(14);
        b.setAllCaps(false);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(247, 243, 236));
        g.setStroke(dp(1), Color.rgb(170, 165, 150));
        g.setCornerRadius(dp(8));
        b.setBackground(g);
        b.setOnClickListener(v -> pickTier(pkg, tier));
        row.addView(b);
    }

    private String tierName(String tier) {
        boolean en = Strings.en();
        if (AppLists.TIER_WHITE.equals(tier)) return en ? "White" : "白";
        if (AppLists.TIER_GREY.equals(tier)) return en ? "Grey" : "灰";
        return en ? "Black" : "黑";
    }

    private void pickTier(final String pkg, String current) {
        boolean en = Strings.en();
        final String[] tiers = {AppLists.TIER_WHITE, AppLists.TIER_GREY, AppLists.TIER_BLACK};
        String[] labels = en
                ? new String[]{"White — safe to use", "Grey — might be useful, no interruptions", "Black — opening it asks right away"}
                : new String[]{"白：放心用", "灰：可能有用，先不打扰", "黑：一打开就问我是不是分神"};
        int checked = 0;
        for (int i = 0; i < tiers.length; i++) if (tiers[i].equals(current)) checked = i;
        new AlertDialog.Builder(this)
                .setTitle(AppLister.labelOf(this, pkg))
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    AppLists.setTier(this, taskId, taskTitle, pkg, tiers[which]);
                    d.dismiss();
                    build();
                })
                .setNegativeButton(en ? "Cancel" : "取消", null)
                .show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
