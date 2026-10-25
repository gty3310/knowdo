package com.ti.tasktimer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * AI 聊天通道客户端（v1.22.0）：App → Supabase Edge Function chat-proxy → OpenRouter。
 * 模型由代理在服务端自动挑当前最强的免费模型并失败顺延，App 不出现任何模型名，
 * 只从流里的 meta 事件读回「本轮实际用的模型 id」，展示在对话页顶部小字。
 * OpenRouter 的 Key 只存在 Supabase secrets 里，App 永远拿不到。
 *
 * 协议：
 * - 成功＝SSE 流：首个 meta 事件带模型 id，之后是 OpenAI 风格的 chunk
 *   （choices[0].delta.content），以 [DONE] 收尾。
 * - 失败＝JSON（代理全败或未配置）：读其中的 friendly 文案提示用户，
 *   不把原始错误抛给用户看。（v1.24.1：测试者模式开启时例外——状态码
 *   与代理的 error 字段经 ErrorReporter 弹悬浮窗亮给测试者，普通用户
 *   口径一字不变。网络/超时/SSE 异常同理只报测试者。）
 * 回调全部切回主线程；send() 本身是阻塞 IO，只能在后台线程调用。
 */
public final class ChatClient {

    public interface Callback {
        /** 收到 meta：本轮实际用的模型 id（可能在重试顺延后才确定，只回一次最终值）。 */
        void onModel(String modelId);
        /** 流式增量：fullText 是到目前为止的完整回复正文（调用方直接整段替换渲染）。 */
        void onDelta(String fullText);
        /** 正常收尾（含 [DONE] 或流自然结束）。 */
        void onDone(String fullText);
        /** 失败：msg 已是给用户看的大白话。 */
        void onError(String friendlyMsg);
    }

    private ChatClient() { }

    /** messages 是 [{role, content}] 的 JSONArray（system 在最前）。 */
    public static void send(Context ctx, JSONArray messages, Callback cb) {
        final Handler main = new Handler(Looper.getMainLooper());
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            HttpURLConnection con = null;
            try {
                JSONObject body = new JSONObject();
                body.put("messages", messages);

                con = (HttpURLConnection) new URL(Supa.BASE + "/functions/v1/chat-proxy").openConnection();
                con.setRequestMethod("POST");
                con.setConnectTimeout(10_000);
                con.setReadTimeout(90_000); // 流式：两次数据之间的间隔上限，免费档慢也不能秒断
                con.setRequestProperty("Content-Type", "application/json");
                con.setRequestProperty("Accept", "text/event-stream");
                con.setRequestProperty("apikey", SupaSecrets.PUBLISHABLE_KEY);
                String at = null;
                try { at = Supa.accessToken(app); } catch (Exception ignored) { }
                con.setRequestProperty("Authorization", "Bearer "
                        + (at != null ? at : SupaSecrets.PUBLISHABLE_KEY));
                con.setDoOutput(true);
                OutputStream out = con.getOutputStream();
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                out.close();

                int status = con.getResponseCode();
                String ctype = con.getContentType() == null ? "" : con.getContentType();
                InputStream in = status >= 400 ? con.getErrorStream() : con.getInputStream();
                if (in == null) {
                    ErrorReporter.report(app, src(), status,
                            Strings.en() ? "Response had no body" : "响应没有内容");
                    postError(main, cb, friendlyFallback(status));
                    return;
                }
                if (!ctype.contains("text/event-stream")) {
                    // 代理的 JSON 回应＝失败（全败/未配置/参数错）：只取 friendly 给用户；
                    // 测试者模式下另把状态码与代理的 error 字段亮出来（v1.24.1）
                    String text = Supa.readAll(in);
                    String friendly = friendlyFallback(status);
                    String detail = "";
                    try {
                        JSONObject j = new JSONObject(text);
                        String f = j.optString("friendly", "");
                        if (!f.isEmpty()) friendly = f;
                        detail = j.optString("error", "");
                        if (detail.isEmpty()) detail = j.optString("message", "");
                        if (detail.isEmpty()) detail = friendly;
                    } catch (Exception ignored) { }
                    ErrorReporter.report(app, src(), status, detail);
                    postError(main, cb, friendly);
                    return;
                }
                boolean[] flags = new boolean[2]; // [0]=收到 [DONE]，[1]=收到正文
                String fullText;
                try {
                    fullText = readSse(in, main, cb, flags);
                } catch (Exception sseEx) {
                    ErrorReporter.report(app, src(), 0,
                            "SSE " + ErrorReporter.summarize(sseEx));
                    postError(main, cb, "网络好像不太通，检查一下再试");
                    return;
                }
                if (!flags[0] && fullText.isEmpty()) {
                    ErrorReporter.report(app, src(), status,
                            Strings.en() ? "SSE stream ended with no content and no [DONE]"
                                    : "SSE 流结束但没收到任何内容（也没有 [DONE]）");
                }
            } catch (Exception e) {
                ErrorReporter.report(app, src(), 0, ErrorReporter.summarize(e));
                postError(main, cb, "网络好像不太通，检查一下再试");
            } finally {
                if (con != null) con.disconnect();
            }
        }).start();
    }

    private static String friendlyFallback(int status) {
        if (status == 500) return "聊天服务还没配置好，请联系开发者";
        return "AI 那边没应答，过一会儿再试";
    }

    /**
     * 读 SSE 流并回调；返回收到的完整正文。flags[0] 置 true 表示正常收到
     * [DONE] 收尾，flags[1] 表示收到过正文——调用方据此识别「流结束却
     * 什么都没有」的解析异常（v1.24.1）。流中途读失败抛异常给调用方。
     */
    private static String readSse(InputStream in, Handler main, Callback cb,
                                  boolean[] flags) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder full = new StringBuilder();
        boolean[] modelSent = {false};
        String event = "";
        String line;
        while ((line = r.readLine()) != null) {
            if (line.isEmpty()) { event = ""; continue; }
            if (line.startsWith("event:")) { event = line.substring(6).trim(); continue; }
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            if ("[DONE]".equals(data)) { flags[0] = true; break; }
            try {
                JSONObject j = new JSONObject(data);
                JSONObject meta = j.optJSONObject("knowdo_meta");
                if (meta != null) {
                    String model = meta.optString("model", "");
                    if (!model.isEmpty() && !modelSent[0]) {
                        modelSent[0] = true;
                        final String m = model;
                        main.post(() -> cb.onModel(m));
                    }
                    continue;
                }
                JSONArray choices = j.optJSONArray("choices");
                if (choices != null && choices.length() > 0) {
                    JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                    if (delta != null) {
                        String piece = delta.optString("content", "");
                        if (!piece.isEmpty()) {
                            flags[1] = true;
                            full.append(piece);
                            final String snapshot = full.toString();
                            main.post(() -> cb.onDelta(snapshot));
                        }
                    }
                }
            } catch (Exception ignored) {
                // 单个坏 chunk 不中断整条流
            }
        }
        r.close();
        final String done = full.toString();
        main.post(() -> cb.onDone(done));
        return done;
    }

    /** 报错悬浮窗的来源名（测试者模式用，与各模块口径一致）。 */
    private static String src() { return Strings.en() ? "Chat" : "聊天"; }

    private static void postError(Handler main, Callback cb, String msg) {
        main.post(() -> cb.onError(msg));
    }

    /** 把本地消息列表转成发给代理的 messages（system 单独放最前由调用方拼）。 */
    public static JSONArray toMessagesJson(String systemPrompt, List<ChatStore.Msg> history,
                                           String newUserText) throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject sys = new JSONObject();
        sys.put("role", "system");
        sys.put("content", systemPrompt);
        arr.put(sys);
        for (ChatStore.Msg m : history) {
            JSONObject o = new JSONObject();
            o.put("role", "assistant".equals(m.role) ? "assistant" : "user");
            o.put("content", m.text);
            arr.put(o);
        }
        if (newUserText != null) {
            JSONObject u = new JSONObject();
            u.put("role", "user");
            u.put("content", newUserText);
            arr.put(u);
        }
        return arr;
    }
}
