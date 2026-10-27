package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 软件内自动更新（v1.27.0）：新版不再靠人去 Drive 手动下载安装包，软件自己查、
 * 自己下、下完只等用户点一下系统安装框。
 *
 * 发布形态（Jarvis 侧执行，App 按此契约读）：每个版本在 Gitee 私有仓
 * knowdo-resources 建一个 release（tag＝v{versionName}），APK 切成约 5MB 的
 * 二进制分片作附件（knowdo-{versionName}.apk.partNN）；仓库 update/ 目录放
 * update.json（contents API 小文件）：{versionCode, versionName, releaseTag,
 * apkSize, apkMd5, shards:[{name,size,md5}], notes}。
 *
 * 下载走实测过的通道：先按 tag 查 release、列 attach_files 对名取 id，再经
 * GET /releases/{id}/attach_files/{fid}/download（带只读令牌、跟随 302 到
 * Gitee 文件 CDN）整片下载——网页直链对仓库令牌是 403、不可用。逐片验
 * size＋md5（已下好的分片校验通过就跳过，断了能续），拼合后验整件
 * size＋md5，全对才进入「待安装」。令牌与模型下载共用（ModelManager.token）。
 *
 * 呈现纪律（用户 UI 定案：安静）：自动检查在启动时节流（每天至多一次）；
 * 下载只在设置页的更新行显示进度，不弹全屏、不出声；下好后一个普通对话框
 * 「新版 X 已下载好」＋安装按钮（每版只弹一次），或在设置页直接点安装。
 * 是否自动下载由 UpdatePrefs 决定：下载网络＝仅 Wi-Fi 时，流量下只记下
 * 「有新版」，等用户到设置页手动点下载。
 *
 * 全部网络/文件方法为阻塞 IO，只能在后台线程调用（状态读取除外）。
 */
public final class AppUpdate {
    private static final String OWNER = "gty3310";
    private static final String REPO = "knowdo-resources";
    private static final String API = "https://gitee.com/api/v5/repos/" + OWNER + "/" + REPO;
    private static final String MANIFEST_PATH = "update/update.json";
    private static final String PREF = "app_update";
    private static final long CHECK_THROTTLE_MS = 20L * 3600 * 1000; // 每天至多自动查一次

    public static final int ST_NONE = 0;        // 没查过/已是最新
    public static final int ST_CHECKING = 1;
    public static final int ST_AVAILABLE = 2;   // 有新版，未下载（网络策略不允许自动下/等手动）
    public static final int ST_DOWNLOADING = 3;
    public static final int ST_READY = 4;       // 已下载并整件校验通过，待安装
    public static final int ST_ERROR = 5;

    /** 状态变化监听（设置页注册，用来刷新更新行；回调在主线程）。 */
    public interface Listener { void onUpdateStateChanged(); }

    private static volatile Listener listener;
    private static volatile boolean working; // 查/下载进行中（防并发重复触发）
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppUpdate() {}

    public static void setListener(Listener l) { listener = l; }

    private static void notifyChanged() {
        MAIN.post(() -> {
            Listener l = listener;
            if (l != null) {
                try { l.onUpdateStateChanged(); } catch (Exception ignored) { }
            }
        });
    }

    // ---------------- 状态存储 ----------------

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static int state(Context c) { return sp(c).getInt("state", ST_NONE); }

    private static void setState(Context c, int st) {
        sp(c).edit().putInt("state", st).apply();
        notifyChanged();
    }

    public static String pendingName(Context c) { return sp(c).getString("pending_name", ""); }
    public static int pendingCode(Context c) { return sp(c).getInt("pending_code", 0); }
    public static long dlDone(Context c) { return sp(c).getLong("dl_done", 0); }
    public static long dlTotal(Context c) { return sp(c).getLong("dl_total", 0); }
    public static String lastError(Context c) { return sp(c).getString("last_error", ""); }
    public static long lastCheckMs(Context c) { return sp(c).getLong("last_check_ms", 0); }

    public static boolean isWorking() { return working; }

    public static int currentCode(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return (int) pi.getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }

    public static String currentName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return BuildInfo.VERSION_NAME;
        }
    }

    private static File updatesDir(Context c) {
        File d = new File(c.getFilesDir(), "updates");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private static File pendingApk(Context c) {
        String n = pendingName(c);
        return n.isEmpty() ? null : new File(updatesDir(c), "knowdo-" + n + ".apk");
    }

    /** 待安装的新版是否就绪（文件在且大小对得上；md5 在下载完成时已验并落状态，这里不重算大文件）。 */
    public static boolean pendingReady(Context c) {
        if (state(c) != ST_READY) return false;
        File f = pendingApk(c);
        return f != null && f.exists() && f.length() == sp(c).getLong("pending_size", -1);
    }

    /** 设置页更新行的一行状态话。 */
    public static String statusText(Context c) {
        boolean en = Strings.en();
        int st = state(c);
        String name = pendingName(c);
        switch (st) {
            case ST_CHECKING: return en ? "Checking…" : "正在检查…";
            case ST_AVAILABLE:
                return en ? "New version " + name + " is available — tap Check for updates to download"
                        : "有新版 " + name + " 可用——点「检查更新」下载";
            case ST_DOWNLOADING: {
                long t = dlTotal(c), d = dlDone(c);
                int pct = t > 0 ? (int) (d * 100 / t) : 0;
                return en ? "Downloading new version… " + pct + "%"
                        : "正在下载新版… " + pct + "%";
            }
            case ST_READY:
                return en ? "New version " + name + " is downloaded — tap to install"
                        : "新版 " + name + " 已下载好——点这里安装";
            case ST_ERROR: {
                String e = lastError(c);
                return en ? "Update failed: " + e : "更新没成：" + e;
            }
            default:
                return en ? "You're on the latest version" : "已是最新版";
        }
    }

    // ---------------- 触发点 ----------------

    /** 启动时自动检查（MainActivity.onCreate 调）：开关开 + 距上次自动查 ≥20 小时才查。 */
    public static void checkOnStartup(Context c) {
        if (!UpdatePrefs.autoCheck(c)) return;
        if (working) return;
        if (System.currentTimeMillis() - lastCheckMs(c) < CHECK_THROTTLE_MS) return;
        startCheck(c.getApplicationContext(), false);
    }

    /** 设置页「检查更新」：手动查；查到新版且网络策略不允许自动下时，也由这次点击直接下。 */
    public static void checkManual(Context c) {
        if (working) return;
        startCheck(c.getApplicationContext(), true);
    }

    /** 设置行在「待安装」态被点：直接走安装（包含无授权时的一次性引导）。 */
    public static void onSettingsRowTapped(Activity a) {
        if (pendingReady(a)) {
            launchInstall(a);
        } else {
            checkManual(a);
        }
    }

    private static void startCheck(final Context app, final boolean manual) {
        working = true;
        setState(app, ST_CHECKING);
        new Thread(() -> {
            try {
                doCheck(app, manual);
            } catch (Exception e) {
                fail(app, e);
            } finally {
                working = false;
                notifyChanged();
            }
        }).start();
    }

    private static void fail(Context c, Exception e) {
        String msg = e.getMessage();
        sp(c).edit().putString("last_error", msg == null ? String.valueOf(e) : msg).apply();
        // 已下载好的待安装版本不被一次失败的检查抹掉
        if (pendingReady(c)) {
            setState(c, ST_READY);
        } else {
            setState(c, ST_ERROR);
        }
        ErrorReporter.report(c, Strings.en() ? "App update" : "软件更新", 0,
                ErrorReporter.summarize(e));
    }

    // ---------------- 检查与下载（后台线程） ----------------

    private static final class Manifest {
        int versionCode;
        String versionName = "";
        String releaseTag = "";
        long apkSize;
        String apkMd5 = "";
        final List<String[]> shards = new ArrayList<>(); // {name, size, md5}
    }

    private static void doCheck(Context c, boolean manual) throws Exception {
        sp(c).edit().putLong("last_check_ms", System.currentTimeMillis()).apply();
        if (ModelManager.token().isEmpty()) {
            throw new Exception(Strings.en() ? "update source is not configured in this build"
                    : "这个安装包没有配置更新源");
        }
        Manifest m = fetchManifest();
        if (m.versionCode <= currentCode(c)) {
            // 已是最新：若存着更旧的待安装包，清掉状态（不删文件，下次同名会复验）
            if (pendingCode(c) <= currentCode(c)) {
                sp(c).edit().remove("pending_code").remove("pending_name").apply();
                setState(c, ST_NONE);
            } else {
                setState(c, ST_READY);
            }
            return;
        }
        boolean samePending = pendingCode(c) == m.versionCode && pendingName(c).equals(m.versionName);
        sp(c).edit().putInt("pending_code", m.versionCode)
                .putString("pending_name", m.versionName)
                .putLong("pending_size", m.apkSize).apply();
        if (samePending && pendingReady(c)) {
            setState(c, ST_READY);
            return;
        }
        boolean mayDownload = manual || downloadAllowed(c);
        if (!mayDownload) {
            setState(c, ST_AVAILABLE);
            return;
        }
        downloadAndAssemble(c, m);
    }

    /** 自动下载的网络策略：Wi-Fi 就下；流量只有在用户选了「Wi-Fi 与流量」时才下。 */
    private static boolean downloadAllowed(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm == null ? null : cm.getActiveNetwork();
            NetworkCapabilities cap = n == null || cm == null ? null : cm.getNetworkCapabilities(n);
            if (cap != null && cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return true;
            if (cap != null && cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return true;
        } catch (Exception ignored) { }
        return !UpdatePrefs.wifiOnly(c);
    }

    private static Manifest fetchManifest() throws Exception {
        String text = fetchContentsText(MANIFEST_PATH);
        JSONObject o = new JSONObject(text);
        Manifest m = new Manifest();
        m.versionCode = o.optInt("versionCode", 0);
        m.versionName = o.optString("versionName", "");
        m.releaseTag = o.optString("releaseTag", "");
        m.apkSize = o.optLong("apkSize", 0);
        m.apkMd5 = o.optString("apkMd5", "");
        JSONArray sh = o.optJSONArray("shards");
        if (sh != null) {
            for (int i = 0; i < sh.length(); i++) {
                JSONObject s = sh.optJSONObject(i);
                if (s == null) continue;
                m.shards.add(new String[]{s.optString("name", ""),
                        String.valueOf(s.optLong("size", 0)), s.optString("md5", "")});
            }
        }
        if (m.versionCode <= 0 || m.shards.isEmpty() || m.releaseTag.isEmpty()) {
            throw new Exception(Strings.en() ? "the update manifest is incomplete"
                    : "更新清单不完整，暂时下不了");
        }
        return m;
    }

    /** 经 contents API 取一个小文本文件（与 ModelManager 同一条已验证形态）。 */
    private static String fetchContentsText(String path) throws Exception {
        String url = API + "/contents/" + URLEncoder.encode(path, "UTF-8").replace("+", "%20")
                + "?access_token=" + URLEncoder.encode(ModelManager.token(), "UTF-8");
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15_000);
        con.setReadTimeout(30_000);
        con.setRequestProperty("User-Agent", "KnowDo-Android");
        int code = con.getResponseCode();
        if (code != 200) {
            con.disconnect();
            throw new Exception("HTTP " + code + "（取更新清单）");
        }
        String body = TaskStore.readAll(con.getInputStream());
        con.disconnect();
        JSONObject o = new JSONObject(body);
        String b64 = o.optString("content", "");
        if (b64.isEmpty()) throw new Exception("更新清单响应里没有内容");
        return new String(Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8);
    }

    private static void downloadAndAssemble(Context c, Manifest m) throws Exception {
        setState(c, ST_DOWNLOADING);
        long total = m.apkSize;
        sp(c).edit().putLong("dl_total", total).putLong("dl_done", 0).apply();
        notifyChanged();

        // 1) 按 tag 查 release id
        long releaseId = findReleaseId(m.releaseTag);
        // 2) 列附件，对名取 id
        JSONArray files = getJsonArray(API + "/releases/" + releaseId + "/attach_files");
        java.util.Map<String, Long> ids = new java.util.HashMap<>();
        for (int i = 0; i < files.length(); i++) {
            JSONObject f = files.optJSONObject(i);
            if (f != null) ids.put(f.optString("name", ""), f.optLong("id", 0));
        }
        // 3) 逐片下载＋校验（已下好且校验过的分片跳过，断点续下）
        File shardDir = new File(updatesDir(c), "shards-" + m.versionName);
        //noinspection ResultOfMethodCallIgnored
        shardDir.mkdirs();
        long done = 0;
        List<File> parts = new ArrayList<>();
        for (String[] s : m.shards) {
            String name = s[0];
            long size = Long.parseLong(s[1]);
            String md5 = s[2];
            Long fid = ids.get(name);
            if (fid == null || fid == 0) {
                throw new Exception((Strings.en() ? "shard missing on the server: " : "服务器上少了分片：") + name);
            }
            File dest = new File(shardDir, name);
            boolean ok = dest.exists() && dest.length() == size
                    && (md5.isEmpty() || md5.equals(ModelManager.md5OfFile(dest)));
            if (!ok) {
                downloadShard(c, releaseId, fid, dest);
                if (!dest.exists() || dest.length() != size) {
                    throw new Exception((Strings.en() ? "shard size mismatch: " : "分片大小不对：") + name);
                }
                if (!md5.isEmpty() && !md5.equals(ModelManager.md5OfFile(dest))) {
                    //noinspection ResultOfMethodCallIgnored
                    dest.delete();
                    throw new Exception((Strings.en() ? "shard check failed: " : "分片校验没过：") + name);
                }
            }
            parts.add(dest);
            done += size;
            sp(c).edit().putLong("dl_done", done).apply();
            notifyChanged();
        }
        // 4) 拼合＋整件校验
        File apk = new File(updatesDir(c), "knowdo-" + m.versionName + ".apk");
        File tmp = new File(updatesDir(c), "knowdo-" + m.versionName + ".apk.tmp");
        FileOutputStream out = new FileOutputStream(tmp);
        byte[] buf = new byte[256 * 1024];
        for (File p : parts) {
            FileInputStream in = new FileInputStream(p);
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
        }
        out.close();
        if (tmp.length() != m.apkSize) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new Exception(Strings.en() ? "assembled size mismatch" : "拼好的安装包大小不对");
        }
        if (!m.apkMd5.isEmpty() && !m.apkMd5.equals(ModelManager.md5OfFile(tmp))) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new Exception(Strings.en() ? "assembled file check failed" : "拼好的安装包校验没过");
        }
        //noinspection ResultOfMethodCallIgnored
        if (apk.exists()) apk.delete();
        if (!tmp.renameTo(apk)) throw new Exception("保存安装包失败");
        sp(c).edit().putLong("pending_size", m.apkSize).apply();
        setState(c, ST_READY);
        promptInstallIfForeground(c);
    }

    private static long findReleaseId(String tag) throws Exception {
        try {
            JSONObject o = getJsonObject(API + "/releases/tags/"
                    + URLEncoder.encode(tag, "UTF-8").replace("+", "%20"));
            long id = o.optLong("id", 0);
            if (id > 0) return id;
        } catch (Exception ignored) { }
        JSONArray arr = getJsonArray(API + "/releases?per_page=50");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && tag.equals(o.optString("tag_name", ""))) {
                long id = o.optLong("id", 0);
                if (id > 0) return id;
            }
        }
        throw new Exception(Strings.en() ? "release not found: " + tag : "找不到这个版本的发行版：" + tag);
    }

    private static JSONObject getJsonObject(String urlNoToken) throws Exception {
        return new JSONObject(getApiText(urlNoToken));
    }

    private static JSONArray getJsonArray(String urlNoToken) throws Exception {
        return new JSONArray(getApiText(urlNoToken));
    }

    private static String getApiText(String urlNoToken) throws Exception {
        String sep = urlNoToken.contains("?") ? "&" : "?";
        String url = urlNoToken + sep + "access_token="
                + URLEncoder.encode(ModelManager.token(), "UTF-8");
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15_000);
        con.setReadTimeout(30_000);
        con.setRequestProperty("User-Agent", "KnowDo-Android");
        int code = con.getResponseCode();
        if (code != 200) {
            con.disconnect();
            throw new Exception("HTTP " + code);
        }
        String body = TaskStore.readAll(con.getInputStream());
        con.disconnect();
        return body;
    }

    /** 一片附件：经 API /download 端点（跟随 302 到 CDN）流式落盘。 */
    private static void downloadShard(Context c, long releaseId, long fileId, File dest) throws Exception {
        String url = API + "/releases/" + releaseId + "/attach_files/" + fileId + "/download"
                + "?access_token=" + URLEncoder.encode(ModelManager.token(), "UTF-8");
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(20_000);
        con.setReadTimeout(120_000);
        con.setRequestProperty("User-Agent", "KnowDo-Android");
        con.setInstanceFollowRedirects(true);
        int code = con.getResponseCode();
        if (code != 200) {
            con.disconnect();
            throw new Exception("HTTP " + code + "（下载分片）");
        }
        InputStream in = con.getInputStream();
        FileOutputStream out = new FileOutputStream(dest);
        byte[] buf = new byte[256 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.close();
        in.close();
        con.disconnect();
    }

    // ---------------- 安装 ----------------

    /** 下载完成时若正有前台页面，顺手弹一次「已下载好」对话框（每版只弹一次）。 */
    private static void promptInstallIfForeground(final Context c) {
        final Activity a = KnowDoApp.resumedActivity();
        if (a == null) return;
        MAIN.post(() -> maybePromptInstall(a));
    }

    /**
     * 若有已下载好的新版且这一版还没弹过提示，就弹普通对话框（安装／稍后）。
     * MainActivity.onResume 也调它：下载在后台完成、人当时不在软件里时，
     * 下次回到主界面补弹一次。已弹过就不再弹（设置页里随时能装）。
     */
    public static void maybePromptInstall(final Activity a) {
        if (a.isFinishing()) return;
        if (!pendingReady(a)) return;
        int vc = pendingCode(a);
        if (sp(a).getInt("prompted_vc", 0) >= vc) return;
        sp(a).edit().putInt("prompted_vc", vc).apply();
        final boolean en = Strings.en();
        new AlertDialog.Builder(a)
                .setTitle(en ? "New version downloaded" : "新版已下载好")
                .setMessage(en ? "KnowDo " + pendingName(a) + " is ready to install. "
                                + "Installing keeps all your data."
                        : "知行 " + pendingName(a) + " 已经下载好，点「安装」就会跳到系统安装确认。"
                        + "覆盖安装，你的数据都还在")
                .setPositiveButton(en ? "Install" : "安装", (d, w) -> launchInstall(a))
                .setNegativeButton(en ? "Later" : "稍后", null)
                .show();
    }

    /**
     * 拉起系统安装：先查「安装未知应用」授权——没授权时给一次性引导（去系统设置
     * 为知行打开），授权回来后用户在设置页更新行点一下即装。有授权时经
     * FileProvider 分享 APK 拉起系统安装框，用户亲手点「安装」（安卓硬规矩，
     * 普通应用不能静默安装）。
     */
    public static void launchInstall(final Activity a) {
        final boolean en = Strings.en();
        File apk = pendingApk(a);
        if (!pendingReady(a) || apk == null) {
            Toast.makeText(a, en ? "The new version isn't downloaded yet"
                    : "新版还没下载好", Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(a)
                    .setTitle(en ? "One permission needed" : "需要一个授权")
                    .setMessage(en ? "To install updates it downloads itself, KnowDo needs "
                                    + "\"Install unknown apps\" allowed once. Tap below, turn it on "
                                    + "for KnowDo, then come back and tap Install again."
                            : "知行自己下载的新版要装上，需要先给它一次「安装未知应用」的许可。"
                            + "点下面去系统设置里为知行打开，回来再点一次安装就行，只用授权这一次")
                    .setPositiveButton(en ? "Open settings" : "去授权", (d, w) -> {
                        try {
                            Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + a.getPackageName()));
                            a.startActivity(i);
                        } catch (Exception e) {
                            Toast.makeText(a, en ? "Couldn't open that settings page"
                                    : "那个设置页没打开，去系统设置里手动找一下", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton(en ? "Later" : "稍后", null)
                    .show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(a, en ? "Couldn't start the installer"
                    : "安装框没拉起来，去设置页再点一次", Toast.LENGTH_LONG).show();
            ErrorReporter.report(a, en ? "App update install" : "软件更新安装", 0,
                    ErrorReporter.summarize(e));
        }
    }
}
