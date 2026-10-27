package com.ti.tasktimer;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务软件名单（v1.26.0）：每个任务一份「常用软件三档」名单——
 * 白（放心用）/ 灰（可能有用，先不打扰）/ 黑（跟任务明显无关，一打开就弹问询）。
 *
 * 名单的生成不在 App 里：开始计时一个还没有名单的任务时，App 把
 * {任务名＋备注＋常用软件清单（至多 100 个）} 写成请求放进云端
 * user_state.app_list_request（SbSync.requestAppList），由云端巡查分类后写回
 * user_state.app_lists，本类只管本地存取、合并与守卫查询。云端两列不可用时
 * （SbSync 探测纪律）整功能静默走本地：名单不来，就用内置通用黑名单兜底。
 *
 * 本地存应用私有文件 app_lists.json，结构与云端 app_lists 一致：
 * { taskId: {"taskTitle":…, "generatedAt":…, "by":…,
 *            "apps": { pkg: {"tier":"white|grey|black","why":…} },
 *            "manual": true（仅用户手改过）, "manualPkgs": [pkg…]（手改过的包） } }
 * 合并口径（与 GuardRules 同纪律）：本地带 manual 的手改条目（按 manualPkgs
 * 逐个软件）不被云端覆盖，其余以云端为准。
 *
 * 待生成状态只记在本类 prefs（pending 集）：请求写云成功才标记，名单到达即清。
 * 同一任务只请求一次（按 taskId；任务改名不重请）。
 */
public final class AppLists {
    public static final String TIER_WHITE = "white";
    public static final String TIER_GREY = "grey";
    public static final String TIER_BLACK = "black";
    public static final int MAX_APPS = 100;
    private static final String FILE = "app_lists.json";
    private static final String PREF = "app_lists";

    private AppLists() {}

    public static boolean enabled(Context c) { return ScreenGuardPrefs.appListsEnabled(c); }

    // ---------------- 本地存取 ----------------

    private static File file(Context c) { return new File(c.getFilesDir(), FILE); }

    public static synchronized JSONObject loadAll(Context c) {
        try {
            File f = file(c);
            if (!f.exists()) return new JSONObject();
            String s = TaskStore.readAll(new FileInputStream(f));
            return new JSONObject(s);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static synchronized void saveAll(Context c, JSONObject all) {
        try {
            FileOutputStream out = new FileOutputStream(file(c));
            out.write(all.toString().getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Exception ignored) { }
    }

    /** 某任务的名单条目；还没有（未生成/未请求）返回 null。 */
    public static synchronized JSONObject entry(Context c, String taskId) {
        if (taskId == null) return null;
        JSONObject e = loadAll(c).optJSONObject(taskId);
        return e;
    }

    public static boolean hasList(Context c, String taskId) { return entry(c, taskId) != null; }

    // ---------------- 待生成标记 ----------------

    private static android.content.SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static synchronized boolean isPending(Context c, String taskId) {
        return prefs(c).getStringSet("pending", new java.util.HashSet<>()).contains(taskId);
    }

    static synchronized void markPending(Context c, String taskId) {
        java.util.Set<String> s = new java.util.HashSet<>(
                prefs(c).getStringSet("pending", new java.util.HashSet<>()));
        s.add(taskId);
        prefs(c).edit().putStringSet("pending", s).apply();
    }

    static synchronized void clearPending(Context c, String taskId) {
        java.util.Set<String> s = new java.util.HashSet<>(
                prefs(c).getStringSet("pending", new java.util.HashSet<>()));
        if (s.remove(taskId)) prefs(c).edit().putStringSet("pending", s).apply();
    }

    // ---------------- 内置通用兜底名单（只判黑一档） ----------------

    /** 包名 → {显示名, 理由}：主流短视频/娱乐/购物/游戏。 */
    private static final Map<String, String[]> FALLBACK = new LinkedHashMap<>();
    static {
        String why = "通用黑名单：娱乐、购物或游戏，跟手头任务多半无关";
        FALLBACK.put("com.ss.android.ugc.aweme", new String[]{"抖音", why});
        FALLBACK.put("com.ss.android.ugc.aweme.lite", new String[]{"抖音极速版", why});
        FALLBACK.put("com.ss.android.ugc.live", new String[]{"抖音火山版", why});
        FALLBACK.put("com.ss.android.ugc.trill", new String[]{"TikTok", why});
        FALLBACK.put("com.smile.gifmaker", new String[]{"快手", why});
        FALLBACK.put("com.kuaishou.nebula", new String[]{"快手极速版", why});
        FALLBACK.put("com.xingin.xhs", new String[]{"小红书", why});
        FALLBACK.put("com.sina.weibo", new String[]{"微博", why});
        FALLBACK.put("tv.danmaku.bili", new String[]{"哔哩哔哩", why});
        FALLBACK.put("com.taobao.taobao", new String[]{"淘宝", why});
        FALLBACK.put("com.xunmeng.pinduoduo", new String[]{"拼多多", why});
        FALLBACK.put("com.jingdong.app.mall", new String[]{"京东", why});
        FALLBACK.put("com.tencent.tmgp.sgame", new String[]{"王者荣耀", why});
        FALLBACK.put("com.tencent.tmgp.pubgmhd", new String[]{"和平精英", why});
    }

    public static Map<String, String[]> fallback() { return FALLBACK; }

    /** pkg 是否只靠兜底名单判黑（该任务还没有生成名单）。 */
    public static boolean isFallbackBlack(Context c, String taskId, String pkg) {
        return entry(c, taskId) == null && FALLBACK.containsKey(pkg);
    }

    // ---------------- 守卫查询 ----------------

    /**
     * 当前任务下某软件的档位："white"/"grey"/"black"，未分类返回 null。
     * 功能关（设置开关）时一律 null。任务还没有名单时只认兜底名单的黑。
     */
    public static String tierFor(Context c, String taskId, String pkg) {
        if (!enabled(c) || pkg == null) return null;
        JSONObject e = entry(c, taskId);
        if (e != null) {
            JSONObject apps = e.optJSONObject("apps");
            JSONObject a = apps == null ? null : apps.optJSONObject(pkg);
            if (a == null) return null;
            String t = a.optString("tier", "");
            return (TIER_WHITE.equals(t) || TIER_GREY.equals(t) || TIER_BLACK.equals(t)) ? t : null;
        }
        return FALLBACK.containsKey(pkg) ? TIER_BLACK : null;
    }

    public static String whyFor(Context c, String taskId, String pkg) {
        JSONObject e = entry(c, taskId);
        if (e != null) {
            JSONObject apps = e.optJSONObject("apps");
            JSONObject a = apps == null ? null : apps.optJSONObject(pkg);
            return a == null ? "" : a.optString("why", "");
        }
        String[] f = FALLBACK.get(pkg);
        return f == null ? "" : f[1];
    }

    /** 问询页副文案（名单黑命中时用）：点明这个软件在当前任务的黑名单里。 */
    public static String reasonFor(Context c, String taskId, String pkg, String taskTitle) {
        String label = AppLister.labelOf(c, pkg);
        String t = taskTitle == null ? "" : taskTitle;
        if (entry(c, taskId) != null) {
            String why = whyFor(c, taskId, pkg);
            return "你打开了「" + label + "」，它在「" + t + "」的黑名单里"
                    + (why.isEmpty() ? "" : "（" + why + "）");
        }
        return "你打开了「" + label + "」，它在通用黑名单里（「" + t + "」的名单还在生成中）";
    }

    // ---------------- 生成请求（开始计时时收口） ----------------

    /**
     * 开始计时时调（TimerService 的 START/RESUME 收口）：任务还没有名单、
     * 也没有在途请求、功能开着、已登录云端 → 采集常用软件清单并经
     * SbSync.requestAppList 写云端请求。采集与写云都在后台线程，失败静默
     * （下次开始同一任务会再试，在途标记只在写成功后才落）。
     */
    public static void maybeRequest(Context c, TaskItem task) {
        if (task == null || task.id == null || task.id.isEmpty()) return;
        if (!enabled(c)) return;
        if (!Supa.isLoggedIn(c)) return;
        if (hasList(c, task.id) || isPending(c, task.id)) return;
        final Context app = c.getApplicationContext();
        final String taskId = task.id;
        final String title = task.title;
        final String note = task.note == null ? "" : task.note;
        new Thread(() -> {
            try {
                JSONArray apps = collectApps(app);
                SbSync.requestAppList(app, taskId, title, note, apps);
            } catch (Exception ignored) { }
        }).start();
    }

    /**
     * 常用软件清单：已安装可启动应用，按近 30 天使用时长（其次最近使用）
     * 排序取前 100；拿不到使用统计（没权限/全零）时退化为全部可启动应用
     * 按名称排序取前 100。元素 {"pkg":…, "label":…}。
     */
    public static JSONArray collectApps(Context c) {
        JSONArray out = new JSONArray();
        try {
            List<AppLister.AppInfo> launch = AppLister.launchableApps(c); // 已按名称排序
            final Map<String, long[]> usage = new HashMap<>(); // pkg -> [总时长, 最近使用]
            boolean usageOk = false;
            try {
                UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
                if (usm != null) {
                    long now = System.currentTimeMillis();
                    List<UsageStats> stats = usm.queryUsageStats(
                            UsageStatsManager.INTERVAL_DAILY, now - 30L * 24 * 3600 * 1000, now);
                    if (stats != null) {
                        for (UsageStats s : stats) {
                            long[] agg = usage.get(s.getPackageName());
                            if (agg == null) { agg = new long[2]; usage.put(s.getPackageName(), agg); }
                            agg[0] += s.getTotalTimeInForeground();
                            agg[1] = Math.max(agg[1], s.getLastTimeUsed());
                            if (s.getTotalTimeInForeground() > 0) usageOk = true;
                        }
                    }
                }
            } catch (Exception ignored) { }
            List<AppLister.AppInfo> ordered = new ArrayList<>(launch);
            if (usageOk) {
                Collections.sort(ordered, (a, b) -> {
                    long[] ua = usage.get(a.pkg), ub = usage.get(b.pkg);
                    long ta = ua == null ? 0 : ua[0], tb = ub == null ? 0 : ub[0];
                    if (ta != tb) return Long.compare(tb, ta);
                    long la = ua == null ? 0 : ua[1], lb = ub == null ? 0 : ub[1];
                    if (la != lb) return Long.compare(lb, la);
                    return a.label.compareTo(b.label);
                });
            }
            int n = Math.min(MAX_APPS, ordered.size());
            for (int i = 0; i < n; i++) {
                JSONObject o = new JSONObject();
                o.put("pkg", ordered.get(i).pkg);
                o.put("label", ordered.get(i).label);
                out.put(o);
            }
        } catch (Exception ignored) { }
        return out;
    }

    // ---------------- 用户手改 ----------------

    /**
     * 名单页里改某个软件的档位：本地即刻生效，该任务条目打 manual、该软件记进
     * manualPkgs（云端合并时这一条永不被覆盖），并踢一次同步把手改推上云。
     * 任务还没有生成名单时，先建一个只有手改条目的空名单（by=manual），
     * 生成名单到达后按 manualPkgs 逐个保护、其余照常填充。
     */
    public static synchronized void setTier(Context c, String taskId, String taskTitle,
                                            String pkg, String tier) {
        if (taskId == null || pkg == null || tier == null) return;
        try {
            JSONObject all = loadAll(c);
            JSONObject e = all.optJSONObject(taskId);
            if (e == null) {
                e = new JSONObject();
                e.put("taskTitle", taskTitle == null ? "" : taskTitle);
                e.put("generatedAt", "");
                e.put("by", "manual");
                JSONObject seed = new JSONObject();
                // 条目一旦存在，守卫就只认条目、不再查兜底名单：先把已安装的
                // 兜底黑名单整份播种进来，免得手改一个、其余兜底软件集体失效
                for (Map.Entry<String, String[]> f : FALLBACK.entrySet()) {
                    try {
                        c.getPackageManager().getApplicationInfo(f.getKey(), 0);
                        JSONObject fa = new JSONObject();
                        fa.put("tier", TIER_BLACK);
                        fa.put("why", f.getValue()[1]);
                        seed.put(f.getKey(), fa);
                    } catch (Exception ignored) { }
                }
                e.put("apps", seed);
                all.put(taskId, e);
            }
            JSONObject apps = e.optJSONObject("apps");
            if (apps == null) { apps = new JSONObject(); e.put("apps", apps); }
            JSONObject a = apps.optJSONObject(pkg);
            if (a == null) { a = new JSONObject(); apps.put(pkg, a); }
            a.put("tier", tier);
            if (!a.has("why") || a.optString("why", "").isEmpty()) a.put("why", "你手动定的");
            JSONArray mp = e.optJSONArray("manualPkgs");
            if (mp == null) { mp = new JSONArray(); e.put("manualPkgs", mp); }
            boolean has = false;
            for (int i = 0; i < mp.length(); i++) if (pkg.equals(mp.optString(i))) { has = true; break; }
            if (!has) mp.put(pkg);
            e.put("manual", true);
            saveAll(c, all);
        } catch (Exception ignored) { }
        kickSync(c);
    }

    // ---------------- 云端合并 ----------------

    /**
     * 与云端 app_lists 合并：以 taskId 为键——本地不存在的采用云端；本地在的
     * 以云端为底，把 manualPkgs 里手改过的软件逐个用本地值盖回（并保留
     * manual/manualPkgs 标记）；本地有、云端还没有的原样保留。结果落本地
     * 并返回（供 SbSync 判断要不要回写云端）。名单到达即清该任务的 pending。
     */
    public static synchronized JSONObject merge(Context c, JSONObject remote) {
        JSONObject local = loadAll(c);
        if (remote == null) return local;
        try {
            JSONObject out = new JSONObject(remote.toString());
            java.util.Iterator<String> it = local.keys();
            while (it.hasNext()) {
                String taskId = it.next();
                JSONObject le = local.optJSONObject(taskId);
                if (le == null) continue;
                JSONObject re = out.optJSONObject(taskId);
                if (re == null) { out.put(taskId, le); continue; }
                JSONArray mp = le.optJSONArray("manualPkgs");
                boolean wholeManual = le.optBoolean("manual", false) && (mp == null || mp.length() == 0);
                if (wholeManual) { out.put(taskId, le); continue; }
                if (mp != null && mp.length() > 0) {
                    JSONObject lApps = le.optJSONObject("apps");
                    JSONObject rApps = re.optJSONObject("apps");
                    if (lApps != null) {
                        if (rApps == null) { rApps = new JSONObject(); re.put("apps", rApps); }
                        for (int i = 0; i < mp.length(); i++) {
                            String pkg = mp.optString(i);
                            JSONObject la = lApps.optJSONObject(pkg);
                            if (la != null) rApps.put(pkg, la);
                        }
                    }
                    re.put("manual", true);
                    re.put("manualPkgs", mp);
                }
            }
            saveAll(c, out);
            // 名单到了：在途标记清掉
            java.util.Iterator<String> rit = remote.keys();
            while (rit.hasNext()) clearPending(c, rit.next());
            return out;
        } catch (Exception e) {
            return local;
        }
    }

    /** 合并签名：taskId＋手改标记＋每软件档位/理由的稳定串，供 SbSync 判断要不要回写。 */
    public static String signature(JSONObject o) {
        if (o == null) return "";
        StringBuilder sb = new StringBuilder();
        try {
            List<String> taskIds = new ArrayList<>();
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) taskIds.add(it.next());
            Collections.sort(taskIds);
            for (String taskId : taskIds) {
                JSONObject e = o.optJSONObject(taskId);
                if (e == null) continue;
                sb.append(taskId).append('|').append(e.optBoolean("manual", false) ? '1' : '0').append('|');
                JSONObject apps = e.optJSONObject("apps");
                if (apps != null) {
                    List<String> pkgs = new ArrayList<>();
                    java.util.Iterator<String> ait = apps.keys();
                    while (ait.hasNext()) pkgs.add(ait.next());
                    Collections.sort(pkgs);
                    for (String pkg : pkgs) {
                        JSONObject a = apps.optJSONObject(pkg);
                        if (a == null) continue;
                        sb.append(pkg).append(':').append(a.optString("tier", ""))
                                .append(':').append(a.optString("why", "")).append(';');
                    }
                }
                sb.append('\n');
            }
        } catch (Exception ignored) { }
        return sb.toString();
    }

    /** 与 GuardRules.kickSync 同办法：手改后踢一次全量同步，把 manual 条目推上云。 */
    public static void kickSync(final Context c) {
        if (!Supa.isLoggedIn(c)) return;
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try { SbSync.syncNow(app, new TaskStore(app)); } catch (Exception ignored) { }
        }).start();
    }
}
