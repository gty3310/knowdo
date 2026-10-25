package com.ti.tasktimer;

import android.content.Context;

/**
 * 聊天「设置提案」的白名单与应用（v1.24.0）：AI 只能提议改这四个设置，
 * 白名单在代码里写死——名单外的 key、或值不合法的，聊天层一律不渲染成卡。
 * 和任务提案同一纪律：先出卡，用户点「应用」才生效；应用走各设置自己的
 * 既有写路径（ScreenGuardPrefs / IdleNag），设置页下次打开即新值。
 */
public final class SettingProposal {
    public static final String KEY_SEMANTIC = "semantic_enabled"; // 语义判定开关（bool）
    public static final String KEY_DIAG = "diag_mode";            // 诊断显示开关（bool）
    public static final String KEY_GUARD = "guard_enabled";        // 屏幕内容守卫总开关（bool）
    public static final String KEY_IDLE_TIER = "idle_nag_tier";    // 空档催促档位（gentle/standard/strict）

    private SettingProposal() { }

    private static boolean en() { return Strings.en(); }

    public static boolean isAllowed(String key) {
        return KEY_SEMANTIC.equals(key) || KEY_DIAG.equals(key)
                || KEY_GUARD.equals(key) || KEY_IDLE_TIER.equals(key);
    }

    private static boolean isBoolKey(String key) { return !KEY_IDLE_TIER.equals(key); }

    /**
     * 把 AI 给的 value 规范化成字符串存进提案（bool→"true"/"false"，档位→原值）；
     * 不合法返回 null（调用方丢弃这条提案）。
     */
    public static String normalize(String key, Object raw) {
        if (!isAllowed(key) || raw == null) return null;
        if (isBoolKey(key)) {
            if (raw instanceof Boolean) return (Boolean) raw ? "true" : "false";
            String s = String.valueOf(raw).trim();
            if ("true".equals(s)) return "true";
            if ("false".equals(s)) return "false";
            return null;
        }
        String s = String.valueOf(raw).trim();
        if (IdleNag.GENTLE.equals(s) || IdleNag.STANDARD.equals(s) || IdleNag.STRICT.equals(s)) {
            return s;
        }
        return null;
    }

    /** 设置的人话名（卡面与概要用）。 */
    public static String nameOf(String key) {
        if (KEY_SEMANTIC.equals(key)) return en() ? "Semantic verdicts" : "语义判定";
        if (KEY_DIAG.equals(key)) return en() ? "Test mode (diagnostics)" : "测试模式";
        if (KEY_GUARD.equals(key)) return en() ? "Screen-content guard" : "屏幕内容守卫";
        if (KEY_IDLE_TIER.equals(key)) return en() ? "Idle nudging" : "空档催促";
        return key;
    }

    /** 卡面概要：「把 X 打开/关掉」或「把 X 改成 严格」。 */
    public static String describe(String key, String value) {
        if (isBoolKey(key)) {
            boolean on = "true".equals(value);
            return en()
                    ? "Turn " + nameOf(key) + (on ? " on" : " off")
                    : "把「" + nameOf(key) + (on ? "」打开" : "」关掉");
        }
        return en()
                ? "Set " + nameOf(key) + " to " + IdleNag.profileName(value)
                : "把「" + nameOf(key) + "」改成「" + IdleNag.profileName(value) + "」";
    }

    /**
     * 应用一条设置提案（用户点了「应用」才走到这里）：走各设置既有写路径。
     * 成功返回 null，失败返回给用户看的原因。
     */
    public static String apply(Context c, TaskStore store, String key, String value) {
        try {
            switch (key) {
                case KEY_SEMANTIC:
                    ScreenGuardPrefs.setSemanticEnabled(c, "true".equals(value));
                    return null;
                case KEY_DIAG:
                    ScreenGuardPrefs.setDiagMode(c, "true".equals(value));
                    return null;
                case KEY_GUARD:
                    ScreenGuardPrefs.setEnabled(c, "true".equals(value));
                    return null;
                case KEY_IDLE_TIER:
                    // 与设置页选档同一套：换档位＋立刻重估催促链＋云端同步一份
                    IdleNag.applyProfile(c, value);
                    IdleAlarm.reevaluate(c);
                    EndNag.reevaluate(c);
                    if (store != null && c instanceof android.app.Activity) {
                        AccountUi.runSync((android.app.Activity) c, store, () -> { }, false);
                    }
                    return null;
                default:
                    return en() ? "That setting can't be changed from chat" : "这个设置不能在聊天里改";
            }
        } catch (Exception e) {
            return en() ? "Couldn't apply that — settings unchanged" : "没改上，设置没动";
        }
    }
}
