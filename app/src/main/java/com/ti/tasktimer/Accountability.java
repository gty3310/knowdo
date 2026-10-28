package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 黄图问责邮件设置（v1.28.0 feature 7）：屏幕内容守卫终判为黄色内容、且这里
 * 的开关打开时，把当场在手机内存里打码的截图发到用户自设的问责邮箱（发送链
 * 见 AccountabilityMailer，服务端防滥发与发信见 Edge Function accountability-mail）。
 * 这是守卫「截图从不上传」红线的唯一例外：只发打码图、只在开关开时、只发给
 * 用户自己填的收件人；原图照旧不落盘、不上传。
 *
 * 存储与同步口径仿 HabitDisplay：本地 SharedPreferences ＋云端 user_state.accountability
 * （jsonb 单对象 {"enabled": bool, "recipients": [..], "subject": str, "body": str}）。
 * 列未就绪时完全不碰云端、只用本地（SbSync 探测纪律）；本机没存过且云端有值 →
 * 采用云端；本机存过且与云端不同 → 回写本机值。
 *
 * 作者账号预填（用户 2026-10-28 亲定）：登录邮箱是作者本人时，首次打开设置
 * 把问责邮箱预填为两个默认地址，只填一次——用户之后删改以用户为准，不再塞回。
 * 其他账号永不预填。注意 wpsuig@hotmail.com 是已退信的死地址，绝不能出现在预填里。
 */
public final class Accountability {
    private static final String PREF = "accountability";

    /** 设置页醒目说明（用户亲定原文，一字不改）。 */
    public static final String NOTICE =
            "现在是测试版，这些邮件借用作者郭天宇的邮箱通道发送，发送内容作者可以看得见";

    private static final String AUTHOR_EMAIL = "guoty3310@gmail.com";
    private static final String[] AUTHOR_DEFAULT_RECIPIENTS = {
            "wpsuig@gmail.com", "xingyun2012ca@hotmail.com"};

    private Accountability() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 本机是否存过问责设置（区分「没设置过，用默认/云端」与「用户在本机定过」）。 */
    public static boolean hasLocal(Context c) { return sp(c).getBoolean("has_local", false); }

    // ---------------- 开关（默认关） ----------------

    public static boolean enabled(Context c) {
        return sp(c).getBoolean("enabled", false);
    }

    public static void setEnabled(Context c, boolean on) {
        sp(c).edit().putBoolean("has_local", true).putBoolean("enabled", on).apply();
        GuardRules.kickSync(c);
    }

    // ---------------- 问责邮箱（可多个） ----------------

    public static List<String> recipients(Context c) {
        List<String> out = new ArrayList<>();
        String raw = sp(c).getString("recipients", "");
        if (raw != null) {
            for (String s : raw.split("\n")) {
                String t = s.trim();
                if (!t.isEmpty()) out.add(t);
            }
        }
        return out;
    }

    /** 编辑弹窗的初始文本：一行一个邮箱。 */
    public static String recipientsText(Context c) {
        StringBuilder sb = new StringBuilder();
        for (String r : recipients(c)) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(r);
        }
        return sb.toString();
    }

    public static void setRecipients(Context c, String text) {
        List<String> list = parseRecipients(text);
        StringBuilder sb = new StringBuilder();
        for (String r : list) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(r);
        }
        sp(c).edit().putBoolean("has_local", true).putString("recipients", sb.toString()).apply();
        GuardRules.kickSync(c);
    }

    /** 一行一个（也容忍逗号/分号分隔），去空行、去重、只留带 @ 的。 */
    public static List<String> parseRecipients(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String s : text.split("[\n,;，；]+")) {
            String t = s.trim();
            if (!t.isEmpty() && t.contains("@") && !out.contains(t)) out.add(t);
        }
        return out;
    }

    // ---------------- 邮件标题 / 正文模板（{时间} {任务} 占位符，服务端发送时替换） ----------------

    public static String defaultSubject() {
        return Strings.en() ? "KnowDo guard alert: sexual content detected"
                : "知行守卫提醒：检测到黄色内容";
    }

    public static String defaultBody() {
        if (Strings.en()) {
            return "KnowDo's screen guard detected sexual/explicit content at {time} "
                    + "(current task: {task}).\n"
                    + "The blurred screenshot is attached — the original was never saved or "
                    + "uploaded; the attachment is the version blurred on the phone at the moment.\n"
                    + "This email was sent automatically by KnowDo's accountability mail feature.";
        }
        return "知行屏幕内容守卫在 {时间} 检测到黄色/暴露内容（当时任务：{任务}）。\n"
                + "打码后的截图见附件——原图从未保存或上传，附件是当场在手机上模糊处理的版本。\n"
                + "这封邮件由知行 KnowDo 的黄图问责功能自动发送。";
    }

    public static String subject(Context c) {
        String s = sp(c).getString("subject", "");
        return (s == null || s.trim().isEmpty()) ? defaultSubject() : s;
    }

    public static void setSubject(Context c, String s) {
        sp(c).edit().putBoolean("has_local", true)
                .putString("subject", s == null ? "" : s.trim()).apply();
        GuardRules.kickSync(c);
    }

    public static String body(Context c) {
        String s = sp(c).getString("body", "");
        return (s == null || s.trim().isEmpty()) ? defaultBody() : s;
    }

    public static void setBody(Context c, String s) {
        sp(c).edit().putBoolean("has_local", true)
                .putString("body", s == null ? "" : s.trim()).apply();
        GuardRules.kickSync(c);
    }

    // ---------------- 作者账号预填（只发生一次） ----------------

    /**
     * 首次打开守卫设置时调：仅当登录邮箱是作者本人、且从未预填过时，把问责
     * 邮箱预填默认的两个地址（标题/正文仍走默认模板，不落存）。prefill_done
     * 标记保证只填一次：用户删掉后不再塞回；其他账号永不预填。
     */
    public static void ensureAuthorPrefill(Context c) {
        SharedPreferences p = sp(c);
        if (p.getBoolean("prefill_done", false)) return;
        if (!AUTHOR_EMAIL.equalsIgnoreCase(Supa.userEmail(c))) return;
        SharedPreferences.Editor e = p.edit().putBoolean("prefill_done", true);
        if (recipients(c).isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String r : AUTHOR_DEFAULT_RECIPIENTS) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(r);
            }
            e.putBoolean("has_local", true).putString("recipients", sb.toString());
        }
        e.apply();
        GuardRules.kickSync(c);
    }

    // ---------------- 云端 JSON（user_state.accountability） ----------------

    public static JSONObject toCloudJson(Context c) {
        try {
            JSONObject o = new JSONObject();
            o.put("enabled", enabled(c));
            JSONArray arr = new JSONArray();
            for (String r : recipients(c)) arr.put(r);
            o.put("recipients", arr);
            o.put("subject", subject(c));
            o.put("body", body(c));
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** 采用云端值（整份替换本地并标 has_local，之后以本机为准参与合并——同口径）。 */
    public static void adoptCloud(Context c, JSONObject o) {
        if (o == null) return;
        SharedPreferences.Editor e = sp(c).edit().putBoolean("has_local", true);
        e.putBoolean("enabled", o.optBoolean("enabled", false));
        JSONArray arr = o.optJSONArray("recipients");
        StringBuilder sb = new StringBuilder();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String t = arr.optString(i, "").trim();
                if (!t.isEmpty()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(t);
                }
            }
        }
        e.putString("recipients", sb.toString());
        e.putString("subject", o.optString("subject", ""));
        e.putString("body", o.optString("body", ""));
        e.apply();
    }

    /** 本机有效值与云端对象是否一致（云端为 null 视为不一致——前提是本机有值）。 */
    public static boolean cloudEquals(Context c, JSONObject remote) {
        if (remote == null) return false;
        try {
            JSONObject mine = toCloudJson(c);
            if (mine.optBoolean("enabled") != remote.optBoolean("enabled", false)) return false;
            if (!mine.optString("subject").equals(remote.optString("subject", ""))) return false;
            if (!mine.optString("body").equals(remote.optString("body", ""))) return false;
            JSONArray a = mine.optJSONArray("recipients");
            JSONArray b = remote.optJSONArray("recipients");
            if (a == null || b == null || a.length() != b.length()) return false;
            for (int i = 0; i < a.length(); i++) {
                if (!a.optString(i).equals(b.optString(i))) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
