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
 *   不把原始错误抛给用户看。
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
                    postError(main, cb, friendlyFallback(status));
                    return;
                }
                if (!ctype.contains("text/event-stream")) {
                    // 代理的 JSON 回应＝失败（全败/未配置/参数错）：只取 friendly
                    String text = Supa.readAll(in);
                    String friendly = friendlyFallback(status);
                    try {
                        JSONObject j = new JSONObject(text);
                        String f = j.optString("friendly", "");
                        if (!f.isEmpty()) friendly = f;
                    } catch (Exception ignored) { }
                    postError(main, cb, friendly);
                    return;
                }
                readSse(in, main, cb);
            } catch (Exception e) {
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

    private static void readSse(InputStream in, Handler main, Callback cb) throws Exception {
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
            if ("[DONE]".equals(data)) break;
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
    }

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
