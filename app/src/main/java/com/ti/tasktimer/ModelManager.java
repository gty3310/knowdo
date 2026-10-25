package com.ti.tasktimer;

import android.content.Context;
import android.util.Base64;

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
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 模型管理器（v1.21.4）：守卫用的模型文件不再打进安装包，改为按需从
 * Gitee 私有仓 gty3310/knowdo-resources 下载到 App 私有目录，誰开哪个
 * 功能就下哪个模型。
 *
 * 清单：仓根目录 model-manifest.json（拉到后缓存在 filesDir，离线时用
 * 缓存清单显示与判状态）。每条含 id/file/version/size/md5/purpose；
 * 主文件被切成 2MiB 分片时带 parts 数组（逐片下完按序拼接，再校验
 * 整文件 md5）；附属文件在 extra_files 里逐个登记 size/md5、各自校验。
 * 条目带 part_encoding="base64" 时（v1.24.4 起，bge 用），分片文件本身是
 * 二进制分片的 Base64 文本（文件名带 .b64）：contents API 解码后得到
 * 的是文本，还须再解一次才得到二进制分片；parts 里的 size 始终是
 * 二进制分片的字节数，校验口径不变。不带该标记的条目行为完全不变。
 *
 * 下载通道：Gitee contents API（access_token 查询参数），与时习书库
 * 同一条已验证的形态；令牌构建期从工程根目录 resources_token.txt 注入
 * ResourcesSecrets（缺失时为空串），永不进仓库。令牌为空时本类所有
 * 下载直接给出「下载源未配置」的明确状态，绝不崩溃。
 *
 * 状态机：未下载／下载中／已就绪／校验失败。已就绪＝主文件与附属文件
 * 都在盘上且校验标记（.verified 文件，记 version+md5）与清单一致；
 * 标记是下载完成并全量校验通过后才写的，状态判断不重算大文件 md5。
 * 全部方法为阻塞 IO，只能在后台线程调用（isReady 等纯本地判断除外）。
 */
public final class ModelManager {
    public static final String ID_NUDENET = "nudenet320n";
    public static final String ID_EMBED = "embed-semantic";

    private static final String OWNER = "gty3310";
    private static final String REPO = "knowdo-resources";
    private static final String API = "https://gitee.com/api/v5/repos/" + OWNER + "/" + REPO + "/contents/";
    private static final String MANIFEST_NAME = "model-manifest.json";

    public enum Status { NOT_CONFIGURED, NOT_DOWNLOADED, DOWNLOADING, READY, VERIFY_FAILED }

    public interface Progress {
        void onProgress(String modelId, long doneBytes, long totalBytes);
    }

    public static final class Extra {
        public String file;
        public long size;
        public String md5;
    }

    public static final class Part {
        public String file;
        public long size;
    }

    public static final class Model {
        public String id;
        public String file;
        public int version;
        public long size;
        public String md5;
        public String purpose;
        /** 清单 part_encoding 原值；"base64" 表示分片是 Base64 文本中转（见类头注）。 */
        public String partEncoding = "";
        public final List<Extra> extras = new ArrayList<>();
        public final List<Part> parts = new ArrayList<>();

        /** 分片是否为 Base64 文本中转下发（须双层解码）。 */
        public boolean partsAreBase64Text() { return "base64".equals(partEncoding); }

        /** 下载总字节（分片按分片计、附属文件另计），给进度条与大小显示用。 */
        public long totalBytes() {
            long t = 0;
            if (!parts.isEmpty()) {
                for (Part p : parts) t += p.size;
            } else {
                t = size;
            }
            for (Extra e : extras) t += e.size;
            return t;
        }
    }

    private ModelManager() {}

    public static String token() {
        try {
            String t = ResourcesSecrets.GITEE_TOKEN;
            return t == null ? "" : t.trim();
        } catch (Throwable e) {
            return "";
        }
    }

    public static boolean sourceConfigured() { return !token().isEmpty(); }

    public static File modelsRoot(Context c) { return new File(c.getFilesDir(), "models"); }

    public static File modelDir(Context c, String id) { return new File(modelsRoot(c), id); }

    /** 模型主文件（已拼装完成）的本地路径；未就绪时也返回应然路径，调用方先判 isReady。 */
    public static File mainFile(Context c, Model m) {
        return new File(modelDir(c, m.id), m.file);
    }

    public static File extraFile(Context c, Model m, String name) {
        return new File(modelDir(c, m.id), name);
    }

    // ---------------- 清单 ----------------

    private static File manifestCache(Context c) { return new File(c.getFilesDir(), MANIFEST_NAME); }

    /** 读清单：优先内存缓存→本地缓存文件；forceNet 时先联网拉新并回写缓存。后台线程调用。 */
    public static synchronized List<Model> manifest(Context c, boolean forceNet) {
        if (forceNet) {
            try {
                String text = fetchText(c, MANIFEST_NAME);
                File f = manifestCache(c);
                FileOutputStream out = new FileOutputStream(f);
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.close();
                return parseManifest(text);
            } catch (Exception ignored) {
                // 拉不到就用缓存清单兜底显示，不让模型页空白
            }
        }
        try {
            File f = manifestCache(c);
            if (f.exists()) {
                return parseManifest(TaskStore.readAll(new FileInputStream(f)));
            }
        } catch (Exception ignored) { }
        return new ArrayList<>();
    }

    public static Model find(Context c, String id) {
        for (Model m : manifest(c, false)) {
            if (m.id.equals(id)) return m;
        }
        return null;
    }

    private static List<Model> parseManifest(String text) throws Exception {
        List<Model> out = new ArrayList<>();
        JSONObject root = new JSONObject(text);
        JSONArray arr = root.optJSONArray("models");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Model m = new Model();
            m.id = o.getString("id");
            m.file = o.getString("file");
            m.version = o.optInt("version", 1);
            m.size = o.optLong("size", 0);
            m.md5 = o.optString("md5", "");
            m.purpose = o.optString("purpose", "");
            m.partEncoding = o.optString("part_encoding", "");
            JSONArray ex = o.optJSONArray("extra_files");
            if (ex != null) {
                for (int j = 0; j < ex.length(); j++) {
                    JSONObject e = ex.getJSONObject(j);
                    Extra x = new Extra();
                    x.file = e.getString("file");
                    x.size = e.optLong("size", 0);
                    x.md5 = e.optString("md5", "");
                    m.extras.add(x);
                }
            }
            JSONArray pa = o.optJSONArray("parts");
            if (pa != null) {
                for (int j = 0; j < pa.length(); j++) {
                    JSONObject e = pa.getJSONObject(j);
                    Part p = new Part();
                    p.file = e.getString("file");
                    p.size = e.optLong("size", 0);
                    m.parts.add(p);
                }
            }
            out.add(m);
        }
        return out;
    }

    // ---------------- 状态 ----------------

    private static File verifiedMark(Context c, Model m) {
        return new File(modelDir(c, m.id), ".verified");
    }

    /** 已就绪＝主文件＋附属文件都在、大小对得上、校验标记与清单的 version/md5 一致。纯本地判断。 */
    public static boolean isReady(Context c, Model m) {
        if (m == null) return false;
        try {
            File dir = modelDir(c, m.id);
            File main = new File(dir, m.file);
            if (!main.exists() || main.length() != m.size) return false;
            for (Extra e : m.extras) {
                File f = new File(dir, e.file);
                if (!f.exists() || f.length() != e.size) return false;
            }
            File mark = verifiedMark(c, m);
            if (!mark.exists()) return false;
            String s = TaskStore.readAll(new FileInputStream(mark)).trim();
            return s.equals(m.version + "|" + m.md5);
        } catch (Exception e) {
            return false;
        }
    }

    public static Status status(Context c, Model m) {
        if (!sourceConfigured() && !isReady(c, m)) return Status.NOT_CONFIGURED;
        if (isReady(c, m)) return Status.READY;
        File dir = modelDir(c, m.id);
        return dir.exists() && dir.list() != null && dir.list().length > 0
                ? Status.VERIFY_FAILED : Status.NOT_DOWNLOADED;
    }

    /** 便捷判断：按 id 查（清单不可用时 false）。 */
    public static boolean isReady(Context c, String id) {
        return isReady(c, find(c, id));
    }

    // ---------------- 下载 ----------------

    /**
     * 下载并校验一个模型（已就绪且版本未变时直接返回 true）。
     * 失败返回 false 并把原因写进 err[0]（校验失败会清掉坏文件，避免半成品被当成好文件）。
     */
    public static synchronized boolean download(Context c, Model m, Progress progress, String[] err) {
        if (!sourceConfigured()) {
            if (err != null) err[0] = "下载源未配置";
            ErrorReporter.report(c, Strings.en() ? "Model download" : "模型下载", 0,
                    Strings.en() ? "Download source not configured" : "下载源未配置");
            return false;
        }
        File dir = modelDir(c, m.id);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        long total = m.totalBytes();
        long[] done = new long[1];
        try {
            if (!m.parts.isEmpty()) {
                // 分片：逐片下（大小必须与清单一致；下坏的单片自动重试，最多 3 次
                // 尝试含首次），再按序拼成主文件校验整文件 md5。整件 md5 不对时分
                // 不清是哪片坏（分片只有大小、没有逐片 md5），照旧整件报错、不无限重试
                boolean b64 = m.partsAreBase64Text();
                File assembled = new File(dir, m.file + ".assembling");
                FileOutputStream out = new FileOutputStream(assembled);
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] buf = new byte[64 * 1024];
                for (Part p : m.parts) {
                    File pf = new File(dir, p.file);
                    if (pf.length() != p.size) {
                        Exception last = null;
                        boolean got = false;
                        for (int attempt = 0; attempt < 3 && !got; attempt++) {
                            try {
                                //noinspection ResultOfMethodCallIgnored
                                if (pf.exists()) pf.delete();
                                downloadOne(c, p.file, pf, b64);
                                got = pf.length() == p.size;
                                if (!got) last = new Exception("分片大小不对：" + p.file);
                            } catch (Exception e) {
                                last = e;
                            }
                        }
                        if (!got) {
                            throw last != null ? last : new Exception("分片大小不对：" + p.file);
                        }
                    }
                    FileInputStream in = new FileInputStream(pf);
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        md.update(buf, 0, n);
                    }
                    in.close();
                    done[0] += p.size;
                    if (progress != null) progress.onProgress(m.id, done[0], total);
                }
                out.close();
                String hex = hex(md.digest());
                if (!hex.equalsIgnoreCase(m.md5)) {
                    //noinspection ResultOfMethodCallIgnored
                    assembled.delete();
                    throw new Exception("校验失败：拼装后的文件对不上（期望 " + m.md5 + "，实际 " + hex + "）");
                }
                File main = new File(dir, m.file);
                //noinspection ResultOfMethodCallIgnored
                if (main.exists()) main.delete();
                if (!assembled.renameTo(main)) throw new Exception("拼装文件落盘失败");
                for (Part p : m.parts) {
                    //noinspection ResultOfMethodCallIgnored
                    new File(dir, p.file).delete();
                }
            } else {
                File main = new File(dir, m.file);
                if (!main.exists() || main.length() != m.size
                        || !md5OfFile(main).equalsIgnoreCase(m.md5)) {
                    downloadOne(c, m.file, main, false);
                    done[0] = m.size;
                    if (progress != null) progress.onProgress(m.id, done[0], total);
                    if (main.length() != m.size || !md5OfFile(main).equalsIgnoreCase(m.md5)) {
                        //noinspection ResultOfMethodCallIgnored
                        main.delete();
                        throw new Exception("校验失败：" + m.file);
                    }
                } else {
                    done[0] = m.size;
                }
            }
            for (Extra e : m.extras) {
                File f = new File(dir, e.file);
                if (!f.exists() || f.length() != e.size || !md5OfFile(f).equalsIgnoreCase(e.md5)) {
                    downloadOne(c, e.file, f, false);
                    if (f.length() != e.size || !md5OfFile(f).equalsIgnoreCase(e.md5)) {
                        //noinspection ResultOfMethodCallIgnored
                        f.delete();
                        throw new Exception("校验失败：" + e.file);
                    }
                }
                done[0] += e.size;
                if (progress != null) progress.onProgress(m.id, done[0], total);
            }
            FileOutputStream mark = new FileOutputStream(verifiedMark(c, m));
            mark.write((m.version + "|" + m.md5).getBytes(StandardCharsets.UTF_8));
            mark.close();
            return true;
        } catch (Exception e) {
            if (err != null) err[0] = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            // 失败口径不变（原因照旧走 err[0] 给调用方）；测试者模式下另弹悬浮窗，
            // 下载类异常的消息里本就带 HTTP 状态码（如「下载 X 失败（HTTP 401）」）
            ErrorReporter.report(c, Strings.en() ? "Model download" : "模型下载", 0,
                    ErrorReporter.summarize(e));
            return false;
        }
    }

    /** 删除一个模型的全部本地文件（腾空间）。 */
    public static synchronized void delete(Context c, String id) {
        File dir = modelDir(c, id);
        deleteRec(dir);
    }

    private static void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRec(k);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    // ---------------- Gitee contents 通道 ----------------

    /** 取一个文本文件（清单用）：contents API 回 JSON，content 字段是 base64。 */
    private static String fetchText(Context c, String path) throws Exception {
        File tmp = new File(c.getCacheDir(), "mm_fetch_" + System.nanoTime());
        try {
            downloadOne(c, path, tmp, false);
            return TaskStore.readAll(new FileInputStream(tmp));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    /**
     * 经 contents API 下一个文件到 dest（传输层一次重试）。响应 JSON 整表读入后
     * 取 content 做 base64 解码——本仓单文件最大就是 2MiB 分片，JSON 约
     * 2.8MB，整表读入在移动端可接受（时习 13MB 级走流式，本仓用不上）。
     * contentIsBase64Text=true 时（清单 part_encoding="base64" 的分片）：文件
     * 内容本身是二进制分片的 Base64 文本，第一次解码得到文本、须再解一次
     * 才得到二进制——contents 接口下发二进制会定点截断、下发 ASCII 完全
     * 可靠，这层文本中转就是为绕开截断（v1.24.4）。
     */
    private static void downloadOne(Context c, String path, File dest, boolean contentIsBase64Text) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String url = API + URLEncoder.encode(path, "UTF-8").replace("+", "%20")
                        + "?access_token=" + URLEncoder.encode(token(), "UTF-8");
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(15_000);
                con.setReadTimeout(60_000);
                con.setRequestProperty("User-Agent", "KnowDo-Android");
                int code = con.getResponseCode();
                if (code != 200) {
                    con.disconnect();
                    throw new Exception("下载 " + path + " 失败（HTTP " + code + "）");
                }
                String body = TaskStore.readAll(con.getInputStream());
                con.disconnect();
                JSONObject o = new JSONObject(body);
                String b64 = o.optString("content", "");
                if (b64.isEmpty()) throw new Exception("响应里没有 content：" + path);
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                if (contentIsBase64Text) {
                    // 解出来的是 Base64 文本（可能带换行），再解一次才是二进制分片
                    data = Base64.decode(data, Base64.DEFAULT);
                }
                FileOutputStream out = new FileOutputStream(dest);
                out.write(data);
                out.close();
                return;
            } catch (Exception e) {
                last = e;
                try { Thread.sleep(800); } catch (InterruptedException ignored2) { }
            }
        }
        throw last != null ? last : new Exception("下载失败：" + path);
    }

    // ---------------- 工具 ----------------

    public static String md5OfFile(File f) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            FileInputStream in = new FileInputStream(f);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            in.close();
            return hex(md.digest());
        } catch (Exception e) {
            return "";
        }
    }

    private static String hex(byte[] bs) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bs) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    /** 大小显示：字节 → MB（一位小数）。 */
    public static String sizeText(long bytes) {
        return String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0);
    }
}
