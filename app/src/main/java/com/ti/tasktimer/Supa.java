package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Supabase 最小客户端（v1.7.0）：只用 HttpURLConnection + org.json，不引官方 SDK。
 * - Auth：邮箱+密码登录/注册（GoTrue），access/refresh token 存本地 SharedPreferences。
 * - PostgREST：所有请求带 apikey 头（publishable key，构建时从本地文件注入，
 *   见 SupaSecrets），登录后另带 Authorization: Bearer <用户 JWT>，RLS 按人隔离。
 * 全部方法都是阻塞 IO，只能在后台线程调用。
 */
public final class Supa {
    public static final String BASE = "https://xkksqsxdluokehdhisau.supabase.co";
    private static final String REST = BASE + "/rest/v1";
    private static final String AUTH = BASE + "/auth/v1";
    private static final String PREF = "supa";

    private Supa() {}

    /** HTTP 层错误：network=true 表示根本没连上（离线），status>0 是服务端返回码。 */
    public static class Err extends Exception {
        public final int status;
        public final boolean network;
        Err(int status, String msg) { super(msg); this.status = status; this.network = false; }
        Err(Exception io) { super(io.getMessage()); this.status = 0; this.network = true; }
    }

    public static class Resp {
        public final int status;
        public final String body;
        Resp(int s, String b) { status = s; body = b; }
        JSONObject json() throws Exception { return new JSONObject(body); }
        JSONArray jsonArr() throws Exception { return new JSONArray(body); }
    }

    // ---------------- token 存取 ----------------
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static boolean isLoggedIn(Context c) {
        String r = prefs(c).getString("refresh", null);
        return r != null && !r.isEmpty();
    }

    public static String userId(Context c) { return prefs(c).getString("uid", ""); }
    public static String userEmail(Context c) { return prefs(c).getString("email", ""); }
    public static String refreshToken(Context c) { return prefs(c).getString("refresh", ""); }

    private static void saveSession(Context c, JSONObject root, String emailFallback) throws Exception {
        String access = root.getString("access_token");
        String refresh = root.getString("refresh_token");
        long expiresIn = root.optLong("expires_in", 3600);
        JSONObject user = root.optJSONObject("user");
        SharedPreferences.Editor e = prefs(c).edit();
        e.putString("access", access);
        e.putString("refresh", refresh);
        e.putLong("expires_at", System.currentTimeMillis() + expiresIn * 1000L);
        if (user != null) {
            e.putString("uid", user.optString("id", ""));
            e.putString("email", user.optString("email", emailFallback == null ? "" : emailFallback));
        } else if (emailFallback != null) {
            e.putString("email", emailFallback);
        }
        e.apply();
    }

    public static void signOut(Context c) {
        // 服务端登出尽力而为（令牌随即本地清除，服务端 refresh 失效与否不影响本机回到桥模式）
        try {
            String at = prefs(c).getString("access", null);
            if (at != null) request("POST", AUTH + "/logout", new JSONObject(), at);
        } catch (Exception ignored) { }
        prefs(c).edit().clear().apply();
    }

    // ---------------- Auth ----------------
    /** 注册。返回 null=已拿到会话（或注册即登录）；返回字符串=需要用户处理的一句话（如去邮箱确认）。 */
    public static String signUp(Context c, String email, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);
        Resp r = request("POST", AUTH + "/signup", body, null);
        if (r.status >= 400) throw new Err(r.status, authErrorText(r.body));
        JSONObject root = r.json();
        if (root.has("access_token")) {
            saveSession(c, root, email);
            return null;
        }
        // 邮箱确认开启时 signup 只返回 user、没有会话
        return "注册已提交。若没能直接登录，请先去邮箱点确认链接，再回来用「登录」。";
    }

    public static void signIn(Context c, String email, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);
        Resp r = request("POST", AUTH + "/token?grant_type=password", body, null);
        if (r.status >= 400) throw new Err(r.status, authErrorText(r.body));
        saveSession(c, r.json(), email);
    }

    /** 用 refresh token 续期；成功 true。网络错误抛 Err(network)，服务端拒绝返回 false。 */
    public static boolean refresh(Context c) throws Exception {
        String rt = refreshToken(c);
        if (rt == null || rt.isEmpty()) return false;
        JSONObject body = new JSONObject();
        body.put("refresh_token", rt);
        Resp r;
        try {
            r = request("POST", AUTH + "/token?grant_type=refresh_token", body, null);
        } catch (Err e) {
            if (e.network) throw e;
            throw e;
        }
        if (r.status >= 400) return false;
        saveSession(c, r.json(), userEmail(c));
        return true;
    }

    /** 拿到当前可用的 access token：没过期直接用，过期先续期。后台线程调用。 */
    public static String accessToken(Context c) throws Exception {
        SharedPreferences p = prefs(c);
        String at = p.getString("access", null);
        long exp = p.getLong("expires_at", 0);
        if (at != null && System.currentTimeMillis() < exp - 60_000L) return at;
        if (!isLoggedIn(c)) return null;
        if (refresh(c)) return prefs(c).getString("access", null);
        return null; // 续期被拒：登录过期
    }

    private static String authErrorText(String body) {
        try {
            JSONObject o = new JSONObject(body);
            String m = o.optString("msg", "");
            if (m.isEmpty()) m = o.optString("error_description", "");
            if (m.isEmpty()) m = o.optString("error", "");
            if (m.isEmpty()) m = o.optString("message", "");
            return m.isEmpty() ? body : m;
        } catch (Exception e) {
            return body;
        }
    }

    // ---------------- PostgREST ----------------
    /** GET，返回行数组。path 例：tasks?select=* */
    public static JSONArray restGet(Context c, String path) throws Exception {
        Resp r = rest(c, "GET", path, null, null);
        if (r.status >= 400) throw new Err(r.status, r.body);
        return r.body.trim().isEmpty() ? new JSONArray() : r.jsonArr();
    }

    /** upsert 一批行（onConflict 指定冲突列），返回服务端 representation（拿回 updated_at）。 */
    public static JSONArray restUpsert(Context c, String table, String onConflict, JSONArray rows) throws Exception {
        Resp r = rest(c, "POST", table + "?on_conflict=" + onConflict, rows,
                "resolution=merge-duplicates,return=representation");
        if (r.status >= 400) throw new Err(r.status, r.body);
        return r.body.trim().isEmpty() ? new JSONArray() : r.jsonArr();
    }

    /** upsert 一行；payload 里没给的列在冲突更新时不动（PostgREST merge-duplicates 语义）。 */
    public static JSONArray restUpsertOne(Context c, String table, String onConflict, JSONObject row) throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(row);
        return restUpsert(c, table, onConflict, arr);
    }

    private static Resp rest(Context c, String method, String path, Object bodyJson, String prefer) throws Exception {
        String at = accessToken(c);
        HttpURLConnection con = open(REST + "/" + path, method);
        con.setRequestProperty("apikey", SupaSecrets.PUBLISHABLE_KEY);
        // 登录后用用户 JWT（RLS 按人隔离）；未登录时用 publishable key 充当 anon
        con.setRequestProperty("Authorization", "Bearer " + (at != null ? at : SupaSecrets.PUBLISHABLE_KEY));
        if (prefer != null) con.setRequestProperty("Prefer", prefer);
        return send(con, bodyJson);
    }

    // ---------------- HTTP 核心 ----------------
    private static Resp request(String method, String url, JSONObject body, String bearer) throws Exception {
        HttpURLConnection con = open(url, method);
        con.setRequestProperty("apikey", SupaSecrets.PUBLISHABLE_KEY);
        if (bearer != null) con.setRequestProperty("Authorization", "Bearer " + bearer);
        return send(con, body);
    }

    private static HttpURLConnection open(String url, String method) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod(method);
        con.setConnectTimeout(15_000);
        con.setReadTimeout(30_000);
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Accept", "application/json");
        return con;
    }

    private static Resp send(HttpURLConnection con, Object bodyJson) throws Exception {
        try {
            if (bodyJson != null) {
                con.setDoOutput(true);
                OutputStream out = con.getOutputStream();
                out.write(bodyJson.toString().getBytes(StandardCharsets.UTF_8));
                out.close();
            }
            int status = con.getResponseCode();
            InputStream in = status >= 400 ? con.getErrorStream() : con.getInputStream();
            String body = in == null ? "" : readAll(in);
            return new Resp(status, body);
        } catch (java.io.IOException e) {
            throw new Err(e);
        } finally {
            con.disconnect();
        }
    }

    static String readAll(InputStream in) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }
}
