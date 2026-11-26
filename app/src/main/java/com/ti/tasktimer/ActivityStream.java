package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 活动流水（v1.98.0，设计定稿 2026-11-26）：与屏幕守卫共用同一条采样管线，
 * 在本地攒最近 3 小时的活动轨迹，喂云端判定分清「真分神／假分神」。
 *
 * 攒法：每个采样帧先由本地拼一句骨架行（应用名＋屏幕首行要点，≤20 字）；
 * 云端判定过的帧用模型顺带产出的 summary（≤20 字）升级当前段的行。相邻
 * 同应用且行相同/高度类似的帧自动合并成一段、只延时长。窗口最近 3 小时，
 * 渲染总量硬顶 500 token（按汉字约 1 字≈1 token 估算），超限从最旧段粗化。
 *
 * 浓缩零 token 铁律：一切合并、降精度、统计都用本地确定性算法，不为流水
 * 新起任何模型调用。流水只存本地文件 activity_stream.json（与
 * guard_events.json 同级），不落原图、不存整屏文字、不进 tasks/时间账、
 * 不同步上云。
 *
 * 段（Seg）：{s 起始, e 最近一帧, pkg, line 一句话, on 是否主线（该帧未被
 * 判分神）, temp 是否假分神临时段（L2/L3 放行时标）}。时长按 e−s 算，
 * 单帧段按一个采样间隔（约 15 秒）计——采样被 aHash 节流时同画面不重复
 * 进帧，段时长是保守估计，只用于模式判断、不作精确计时。
 */
public final class ActivityStream {
    private static final String FILE = "activity_stream.json";
    private static final String PREF = "activity_stream_state";
    static final long WINDOW_MS = 3 * 3600_000L;
    static final long HOUR_MS = 3600_000L;
    private static final long MERGE_GAP_MS = 120_000L;
    private static final long FRAME_NOMINAL_MS = 15_000L;
    private static final int TOKEN_BUDGET = 500;
    private static final int LINE_MAX = 20;

    private ActivityStream() {}

    static final class Seg {
        long s, e;
        String pkg = "";
        String line = "";
        boolean on;
        boolean temp;
        long dur() { return Math.max(e - s, FRAME_NOMINAL_MS); }
    }

    // ---------------- 记帧 ----------------

    /**
     * 采样帧落账（PipelineAnalyzer 每帧调）：r 为本帧最终结论（null＝主线
     * 应用豁免帧，按主线记）；r.category==null 记主线、非空记岔路。
     * r.judgeSummary 非空时顺手升级当前段的行（云端判过的帧）。
     */
    public static synchronized void noteFrame(Context c, TaskItem task, String fgPkg,
                                              String text, ContentAnalyzer.Result r) {
        if (c == null || fgPkg == null || fgPkg.isEmpty()) return;
        try {
            long now = System.currentTimeMillis();
            List<Seg> segs = load(c);
            String line = skeletonLine(c, fgPkg, text);
            boolean on = r == null || r.category == null;
            Seg last = segs.isEmpty() ? null : segs.get(segs.size() - 1);
            if (last != null && fgPkg.equals(last.pkg) && now - last.e <= MERGE_GAP_MS
                    && similar(last.line, line)) {
                last.e = now;
                last.on = on;
            } else {
                Seg s = new Seg();
                s.s = now;
                s.e = now;
                s.pkg = fgPkg;
                s.line = line;
                s.on = on;
                segs.add(s);
                last = s;
            }
            if (r != null && r.judgeSummary != null && !r.judgeSummary.trim().isEmpty()) {
                last.line = trunc(r.judgeSummary.trim(), LINE_MAX);
            }
            prune(segs, now);
            save(c, segs);
        } catch (Exception ignored) { }
    }

    /** 主线应用（任务主软件/允许名单）豁免帧：只记主线段，不带屏幕文字细节。 */
    public static synchronized void noteMainlineFrame(Context c, String fgPkg) {
        if (c == null || fgPkg == null || fgPkg.isEmpty()) return;
        try {
            long now = System.currentTimeMillis();
            List<Seg> segs = load(c);
            String line = trunc(AppLister.labelOf(c, fgPkg), LINE_MAX);
            Seg last = segs.isEmpty() ? null : segs.get(segs.size() - 1);
            if (last != null && fgPkg.equals(last.pkg) && now - last.e <= MERGE_GAP_MS) {
                last.e = now;
                last.on = true;
            } else {
                Seg s = new Seg();
                s.s = now;
                s.e = now;
                s.pkg = fgPkg;
                s.line = line;
                s.on = true;
                segs.add(s);
            }
            prune(segs, now);
            save(c, segs);
        } catch (Exception ignored) { }
    }

    /** 假分神放行（L2 点「在忙正事」/L3 不弹窗）时标当前段为临时任务段。 */
    public static synchronized void markCurrentTempTask(Context c) {
        try {
            List<Seg> segs = load(c);
            if (segs.isEmpty()) return;
            Seg last = segs.get(segs.size() - 1);
            if (System.currentTimeMillis() - last.e > MERGE_GAP_MS) return;
            last.temp = true;
            save(c, segs);
        } catch (Exception ignored) { }
    }

    // ---------------- 渲染（喂判定） ----------------

    /**
     * 现场渲染给云端判定的流水文本：分层合并粒度（越老越粗）＋「但是」结构
     * （常态在前、最近 10 分钟在「但是」之后；无转折则时间顺序平铺、最近在
     * 最后）＋末尾一行本地统计。总量硬顶 500 token，超限逐级粗化。
     */
    public static synchronized String renderForJudge(Context c) {
        try {
            long now = System.currentTimeMillis();
            List<Seg> segs = load(c);
            prune(segs, now);
            if (segs.isEmpty()) return "";
            List<Seg> recent = new ArrayList<>(), mid = new ArrayList<>(),
                    hour = new ArrayList<>(), bg = new ArrayList<>();
            for (Seg s : segs) {
                long age = now - s.e;
                if (age <= 10 * 60_000L) recent.add(s);
                else if (age <= 30 * 60_000L) mid.add(s);
                else if (age <= HOUR_MS) hour.add(s);
                else bg.add(s);
            }
            String bgPart = bg.isEmpty() ? "" : "较早：" + bgSummary(c, bg);
            String hourPart = hour.isEmpty() ? "" : "近1小时：" + groupSummary(c, hour, false);
            String midPart = mid.isEmpty() ? "" : "近半小时：" + groupSummary(c, mid, true);
            String recentPart = joinSegs(recent);
            String stats = statsLine(c, segs, now);

            String olderDominant = dominantPkg(concat(bg, hour, mid));
            String recentDominant = dominantPkg(recent);
            StringBuilder out = new StringBuilder();
            if (!recent.isEmpty() && !olderDominant.isEmpty()
                    && !olderDominant.equals(recentDominant)) {
                List<String> older = new ArrayList<>();
                if (!bgPart.isEmpty()) older.add(bgPart);
                if (!hourPart.isEmpty()) older.add(hourPart);
                if (!midPart.isEmpty()) older.add(midPart);
                out.append(join(older, "；"));
                out.append("\n但是最近10分钟：").append(recentPart);
            } else {
                List<String> parts = new ArrayList<>();
                if (!bgPart.isEmpty()) parts.add(bgPart);
                if (!hourPart.isEmpty()) parts.add(hourPart);
                if (!midPart.isEmpty()) parts.add(midPart);
                if (!recentPart.isEmpty()) parts.add("最近10分钟：" + recentPart);
                out.append(join(parts, "；"));
            }
            // 预算硬顶：逐级粗化——先丢背景层，再丢近1小时层，再把近半小时压成一行
            String body = out.toString();
            if (estTokens(body) + estTokens(stats) > TOKEN_BUDGET && !bgPart.isEmpty()) {
                body = body.replace(bgPart + "；", "").replace(bgPart, "");
            }
            if (estTokens(body) + estTokens(stats) > TOKEN_BUDGET && !hourPart.isEmpty()) {
                body = body.replace(hourPart + "；", "").replace(hourPart, "");
            }
            if (estTokens(body) + estTokens(stats) > TOKEN_BUDGET && mid.size() > 1) {
                String one = "近半小时：" + groupSummary(c, mid, false);
                body = body.replace(midPart, one);
            }
            if (estTokens(body) + estTokens(stats) > TOKEN_BUDGET && recent.size() > 2) {
                // 仍超：最近10分钟只留最后两段（最新的最重要）
                List<Seg> tail = recent.subList(recent.size() - 2, recent.size());
                body = body.replace(recentPart, joinSegs(tail));
            }
            return (body + "\n" + stats).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String statsLine(Context c, List<Seg> segs, long now) {
        double ratio = ratioOf(segs, now, WINDOW_MS);
        long longest = 0;
        for (Seg s : segs) if (s.on) longest = Math.max(longest, s.dur());
        int switches = Math.max(0, segs.size() - 1);
        // 岔路：非主线段按应用汇总时长，取前三
        Map<String, Long> detour = new LinkedHashMap<>();
        for (Seg s : segs) {
            if (s.on) continue;
            Long v = detour.get(s.pkg);
            detour.put(s.pkg, (v == null ? 0 : v) + s.dur());
        }
        List<Map.Entry<String, Long>> ds = new ArrayList<>(detour.entrySet());
        ds.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        StringBuilder d = new StringBuilder();
        for (int i = 0; i < Math.min(3, ds.size()); i++) {
            if (i > 0) d.append("、");
            d.append(AppLister.labelOf(c, ds.get(i).getKey()))
                    .append("约").append(minutes(ds.get(i).getValue())).append("分钟");
        }
        return "统计：近3小时主线占比" + Math.round(ratio * 100) + "%，最长连续专注约"
                + minutes(longest) + "分钟，切换" + switches + "次"
                + (d.length() > 0 ? "，岔路：" + d : "");
    }

    // ---------------- 统计（护栏与渲染共用） ----------------

    /** 窗口内主线时间占比（无数据按 1.0：没有分神证据就不封顶）。 */
    public static synchronized double mainlineRatio(Context c, long windowMs) {
        try {
            List<Seg> segs = load(c);
            return ratioOf(segs, System.currentTimeMillis(), windowMs);
        } catch (Exception e) {
            return 1.0;
        }
    }

    private static double ratioOf(List<Seg> segs, long now, long windowMs) {
        long from = now - windowMs;
        long total = 0, on = 0;
        for (Seg s : segs) {
            long a = Math.max(s.s, from), b = Math.min(s.e + FRAME_NOMINAL_MS, now + FRAME_NOMINAL_MS);
            if (b <= a) continue;
            total += b - a;
            if (s.on) on += b - a;
        }
        return total <= 0 ? 1.0 : (double) on / total;
    }

    // ---------------- L0 判定历史（护栏用，本地 prefs） ----------------

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 记一次「云端判 L0」（触发级）：护栏数近 1 小时已判 L0 次数。 */
    public static synchronized void noteL0(Context c) {
        try {
            long now = System.currentTimeMillis();
            List<Long> ts = l0Times(c);
            ts.add(now);
            saveL0(c, ts, now);
        } catch (Exception ignored) { }
    }

    public static synchronized int l0CountLastHour(Context c) {
        try {
            long now = System.currentTimeMillis();
            int n = 0;
            for (long t : l0Times(c)) if (now - t <= HOUR_MS) n++;
            return n;
        } catch (Exception e) {
            return 0;
        }
    }

    private static List<Long> l0Times(Context c) {
        List<Long> out = new ArrayList<>();
        String raw = prefs(c).getString("l0_ts", "");
        if (raw != null && !raw.isEmpty()) {
            for (String p : raw.split(",")) {
                try { out.add(Long.parseLong(p.trim())); } catch (Exception ignored) { }
            }
        }
        return out;
    }

    private static void saveL0(Context c, List<Long> ts, long now) {
        StringBuilder sb = new StringBuilder();
        for (long t : ts) {
            if (now - t > HOUR_MS) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(t);
        }
        prefs(c).edit().putString("l0_ts", sb.toString()).apply();
    }

    // ---------------- 骨架行与相似度 ----------------

    /** 骨架行：应用名＋屏幕首行要点，截到 ≤20 字。 */
    static String skeletonLine(Context c, String pkg, String text) {
        String label = AppLister.labelOf(c, pkg);
        String first = "";
        if (text != null) {
            for (String ln : text.split("\n")) {
                String t = ln.trim();
                if (!t.isEmpty()) { first = t; break; }
            }
        }
        String line = first.isEmpty() ? label : label + " " + first;
        return trunc(line, LINE_MAX);
    }

    /** 高度类似：去空白后相等、一方包含另一方、或编辑距离小阈值。 */
    static boolean similar(String a, String b) {
        if (a == null || b == null) return false;
        String x = a.replaceAll("\\s+", ""), y = b.replaceAll("\\s+", "");
        if (x.equals(y)) return true;
        if (x.length() >= 4 && y.length() >= 4 && (x.contains(y) || y.contains(x))) return true;
        int th = Math.max(2, Math.min(x.length(), y.length()) / 4);
        return editDistance(x, y, th) <= th;
    }

    private static int editDistance(String a, String b, int cap) {
        int n = a.length(), m = b.length();
        if (Math.abs(n - m) > cap) return cap + 1;
        int[] prev = new int[m + 1], cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            int best = cur[0];
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                best = Math.min(best, cur[j]);
            }
            if (best > cap) return cap + 1;
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[m];
    }

    // ---------------- 分层汇总小件 ----------------

    private static String joinSegs(List<Seg> segs) {
        StringBuilder sb = new StringBuilder();
        for (Seg s : segs) {
            if (sb.length() > 0) sb.append("；");
            sb.append(s.line).append("（约").append(minutes(s.dur())).append("分钟）");
            if (s.temp) sb.append("·临时事");
        }
        return sb.toString();
    }

    /** 相邻同应用合并（merge=true）或按应用汇总：一行一个应用组。 */
    private static String groupSummary(Context c, List<Seg> segs, boolean mergeAdjacent) {
        Map<String, long[]> byPkg = new LinkedHashMap<>(); // pkg -> [总时长, 段数]
        Map<String, String> lineOf = new LinkedHashMap<>();
        String prevPkg = null;
        for (Seg s : segs) {
            long[] v = byPkg.get(s.pkg);
            if (v == null) { v = new long[2]; byPkg.put(s.pkg, v); }
            v[0] += s.dur();
            v[1] += 1;
            if (!mergeAdjacent || !s.pkg.equals(prevPkg)) lineOf.put(s.pkg, s.line);
            prevPkg = s.pkg;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, long[]> e : byPkg.entrySet()) {
            if (sb.length() > 0) sb.append("；");
            sb.append(lineOf.get(e.getKey())).append("（共约").append(minutes(e.getValue()[0])).append("分钟）");
        }
        return sb.toString();
    }

    private static String bgSummary(Context c, List<Seg> segs) {
        Map<String, Long> byPkg = new LinkedHashMap<>();
        long total = 0;
        for (Seg s : segs) {
            Long v = byPkg.get(s.pkg);
            byPkg.put(s.pkg, (v == null ? 0 : v) + s.dur());
            total += s.dur();
        }
        List<Map.Entry<String, Long>> es = new ArrayList<>(byPkg.entrySet());
        es.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, es.size()); i++) {
            if (i > 0) sb.append("、");
            sb.append(AppLister.labelOf(c, es.get(i).getKey())).append("约")
                    .append(total <= 0 ? 0 : Math.round(es.get(i).getValue() * 100.0 / total)).append("%");
        }
        return sb.toString();
    }

    private static String dominantPkg(List<Seg> segs) {
        Map<String, Long> byPkg = new LinkedHashMap<>();
        for (Seg s : segs) {
            Long v = byPkg.get(s.pkg);
            byPkg.put(s.pkg, (v == null ? 0 : v) + s.dur());
        }
        String best = "";
        long bestV = -1;
        for (Map.Entry<String, Long> e : byPkg.entrySet()) {
            if (e.getValue() > bestV) { bestV = e.getValue(); best = e.getKey(); }
        }
        return best;
    }

    private static List<Seg> concat(List<Seg>... lists) {
        List<Seg> out = new ArrayList<>();
        for (List<Seg> l : lists) out.addAll(l);
        return out;
    }

    private static String join(List<String> parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p);
        }
        return sb.toString();
    }

    private static long minutes(long ms) {
        return Math.max(1, Math.round(ms / 60_000.0));
    }

    private static int estTokens(String s) {
        return s == null ? 0 : s.length(); // 汉字约 1 字≈1 token 的保守估算（设计口径）
    }

    private static String trunc(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    // ---------------- 存储 ----------------

    private static File file(Context c) { return new File(c.getFilesDir(), FILE); }

    private static void prune(List<Seg> segs, long now) {
        segs.removeIf(s -> s.e < now - WINDOW_MS);
    }

    private static List<Seg> load(Context c) {
        List<Seg> out = new ArrayList<>();
        try {
            File f = file(c);
            if (!f.exists()) return out;
            String s = TaskStore.readAll(new FileInputStream(f));
            JSONArray arr = new JSONArray(s);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Seg g = new Seg();
                g.s = o.optLong("s", 0);
                g.e = o.optLong("e", g.s);
                g.pkg = o.optString("pkg", "");
                g.line = o.optString("line", "");
                g.on = o.optBoolean("on", true);
                g.temp = o.optBoolean("temp", false);
                out.add(g);
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static void save(Context c, List<Seg> segs) {
        try {
            JSONArray arr = new JSONArray();
            for (Seg g : segs) {
                JSONObject o = new JSONObject();
                o.put("s", g.s);
                o.put("e", g.e);
                o.put("pkg", g.pkg);
                o.put("line", g.line);
                o.put("on", g.on);
                if (g.temp) o.put("temp", true);
                arr.put(o);
            }
            FileOutputStream out = new FileOutputStream(file(c));
            out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Exception ignored) { }
    }
}
