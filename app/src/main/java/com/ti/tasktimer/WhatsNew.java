package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * 更新日志（v1.13.0 首个落地；用户定的长期规矩：每次出新版，安装更新后首次打开必弹，
 * 说明改了什么、为什么改、怎么试）。内容数据在 Changelog（最近 5 版，最新在最上），
 * 本类负责两件事：升级后首次打开的弹框（maybeShow），与随时翻看的同一页（show，
 * 设置页「通用」组有入口）。
 *
 * 弹一次的机制：本地记「已展示过的最高 versionCode」。
 * - 当前 versionCode 不大于记录 → 不弹。
 * - 没有记录（记录键从未写过）时分两种：本功能上线前装的旧版升上来（安装时间与更新时间
 *   不一致，是升级安装）→ 照常弹；真正的全新安装 → 只记下当前版本、不弹。
 * 弹出前先落记录，保证只弹一次（弹框被其他全屏页盖住、进程被回收也不会重复弹）。
 */
public final class WhatsNew {
    private static final String PREF = "whats_new";
    private static final String KEY_SHOWN = "shown_version_code";

    private WhatsNew() {}

    public static void maybeShow(Activity a) {
        try {
            PackageInfo pi = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
            int cur = (int) pi.getLongVersionCode();
            SharedPreferences p = a.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            int shown = p.getInt(KEY_SHOWN, 0);
            if (shown >= cur) return;
            if (shown == 0) {
                // 无记录：升级安装（更新时间晚于安装时间）才弹；全新安装只记录不弹。
                // 系统时钟/安装器给的时间戳留 2 秒宽限，避免同秒写入误判。
                boolean upgraded = pi.lastUpdateTime > pi.firstInstallTime + 2000;
                if (!upgraded) {
                    p.edit().putInt(KEY_SHOWN, cur).apply();
                    return;
                }
            }
            p.edit().putInt(KEY_SHOWN, cur).apply(); // 先记下：只弹一次
            show(a);
        } catch (Exception ignored) {
            // 弹框失败绝不挡主界面启动
        }
    }

    /** 更新日志页（弹框版）：最近 5 个版本纵向排列、可滚动；设置入口与首弹共用。 */
    public static void show(Activity a) {
        TextView tv = new TextView(a);
        float d = a.getResources().getDisplayMetrics().density;
        int padX = (int) (22 * d), padY = (int) (14 * d);
        tv.setPadding(padX, padY, padX, padY);
        tv.setTextSize(15);
        tv.setText(buildText(), TextView.BufferType.SPANNABLE);
        ScrollView sv = new ScrollView(a);
        sv.addView(tv);
        new AlertDialog.Builder(a)
                .setTitle(Strings.whatsNewEntry())
                .setView(sv)
                .setPositiveButton(Strings.alarmGotIt(), null)
                .show();
    }

    private static CharSequence buildText() {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        List<Changelog.Entry> entries = Changelog.entries();
        for (int i = 0; i < entries.size(); i++) {
            Changelog.Entry e = entries.get(i);
            if (i > 0) sb.append("\n");
            int start = sb.length();
            sb.append(e.version).append(" · ").append(e.date).append("\n");
            sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new RelativeSizeSpan(1.12f), start, sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            for (Changelog.Group g : e.groups) {
                bold(sb, g.cat + (Strings.en() ? "" : "："));
                for (String item : g.items) sb.append("• ").append(item).append("\n");
            }
            if (e.why != null) {
                bold(sb, Strings.en() ? "Why: " : "为什么：");
                sb.append(e.why).append("\n");
            }
            if (e.tryIt != null) {
                bold(sb, Strings.en() ? "Try it: " : "怎么试：");
                sb.append(e.tryIt).append("\n");
            }
        }
        return sb;
    }

    private static void bold(SpannableStringBuilder sb, String text) {
        int start = sb.length();
        sb.append(text);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
