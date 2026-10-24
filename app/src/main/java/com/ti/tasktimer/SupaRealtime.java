package com.ti.tasktimer;

import android.content.Context;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Supabase Realtime 最小客户端（v1.7.0）：Phoenix 协议 over WebSocket（org.java-websocket）。
 * 一个频道 realtime:knowdo 绑 tasks 与 user_state 两张表的 postgres_changes 全事件，
 * 25 秒心跳，断线指数退避重连，重连成功后由监听方跑一次全量对账补漏。
 * access_token 放在 join 包里，令牌刷新后用 access_token 事件更新（RLS 按人隔离靠它）。
 */
public final class SupaRealtime {
    public interface Listener {
        void onTaskRow(JSONObject record, String type);
        void onUserStateRow(JSONObject record);
        void onJoined();
    }

    private static final String TOPIC = "realtime:knowdo";
    private static SupaRealtime instance;

    private final Context app;
    private volatile Listener listener;
    private WsClient client;
    private volatile boolean manualClose = false;
    private volatile boolean joined = false;
    private volatile long refSeq = 0;
    private volatile String sentToken = "";
    private final Map<Long, String> bindings = new HashMap<>(); // binding id → table
    private Thread heartbeatThread;
    private Thread reconnectThread;
    private volatile int backoffIdx = 0;

    private SupaRealtime(Context c, Listener l) {
        app = c.getApplicationContext();
        listener = l;
    }

    /** 确保连接存在（已连上且已 join 就只顺带检查令牌是否要更新）。SyncService 在 Supabase 模式下周期调用。 */
    public static synchronized void ensure(Context c, Listener l) {
        if (!Supa.isLoggedIn(c)) return;
        if (instance == null) instance = new SupaRealtime(c, l);
        else instance.listener = l; // 服务重建时把回调换成新实例的，别让旧服务的回调悬着
        instance.connectIfNeeded();
    }

    public static synchronized void shutdown() {
        if (instance != null) {
            instance.closeInternal();
            instance = null;
        }
    }

    public static synchronized boolean isJoined() {
        return instance != null && instance.joined;
    }

    private void connectIfNeeded() {
        if (manualClose) return;
        if (client != null) {
            ReadyState rs = client.getReadyState();
            if (rs == ReadyState.OPEN || rs == ReadyState.NOT_YET_CONNECTED) {
                maybeUpdateToken();
                return;
            }
        }
        new Thread(() -> {
            String token;
            try {
                token = Supa.accessToken(app);
            } catch (Exception e) {
                token = null;
            }
            if (token == null) { scheduleReconnect(); return; }
            startClient(token);
        }, "supa-rt-connect").start();
    }

    private void startClient(String token) {
        try {
            URI uri = new URI("wss://" + host() + "/realtime/v1/websocket?apikey="
                    + SupaSecrets.PUBLISHABLE_KEY + "&vsn=2.0.0");
            Map<String, String> headers = new HashMap<>();
            headers.put("apikey", SupaSecrets.PUBLISHABLE_KEY);
            WsClient ws = new WsClient(uri, headers, token);
            client = ws;
            ws.connect();
        } catch (Exception e) {
            scheduleReconnect();
        }
    }

    private static String host() {
        // Supa.BASE = https://<host>
        return Supa.BASE.substring("https://".length());
    }

    private void sendJoin(String token) {
        try {
            JSONObject config = new JSONObject();
            JSONArray pcs = new JSONArray();
            pcs.put(new JSONObject().put("event", "*").put("schema", "public").put("table", "tasks"));
            pcs.put(new JSONObject().put("event", "*").put("schema", "public").put("table", "user_state"));
            config.put("postgres_changes", pcs);
            config.put("broadcast", new JSONObject().put("ack", false).put("self", false));
            config.put("presence", new JSONObject().put("key", ""));
            JSONObject payload = new JSONObject();
            payload.put("config", config);
            payload.put("access_token", token);
            send(TOPIC, "phx_join", payload);
            sentToken = token;
        } catch (Exception ignored) { }
    }

    private void maybeUpdateToken() {
        if (!joined) return;
        new Thread(() -> {
            try {
                String t = Supa.accessToken(app);
                if (t != null && !t.equals(sentToken)) {
                    JSONObject payload = new JSONObject();
                    payload.put("access_token", t);
                    send(TOPIC, "access_token", payload);
                    sentToken = t;
                }
            } catch (Exception ignored) { }
        }, "supa-rt-token").start();
    }

    private void send(String topic, String event, JSONObject payload) {
        WsClient ws = client;
        if (ws == null || !ws.isOpen()) return;
        try {
            long ref = ++refSeq;
            JSONObject msg = new JSONObject();
            msg.put("topic", topic);
            msg.put("event", event);
            msg.put("payload", payload == null ? new JSONObject() : payload);
            msg.put("ref", String.valueOf(ref));
            if ("phx_join".equals(event)) msg.put("join_ref", String.valueOf(ref));
            ws.send(msg.toString());
        } catch (Exception ignored) { }
    }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatThread = new Thread(() -> {
            while (!manualClose && client != null && !client.isClosed()) {
                try { Thread.sleep(25_000); } catch (InterruptedException e) { return; }
                if (client != null && client.isOpen()) {
                    send("phoenix", "heartbeat", new JSONObject());
                }
            }
        }, "supa-rt-heartbeat");
        heartbeatThread.start();
    }

    private void stopHeartbeat() {
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
            heartbeatThread = null;
        }
    }

    private void scheduleReconnect() {
        if (manualClose) return;
        if (reconnectThread != null && reconnectThread.isAlive()) return;
        final long[] delays = {2_000, 5_000, 10_000, 20_000, 30_000};
        reconnectThread = new Thread(() -> {
            try {
                Thread.sleep(delays[Math.min(backoffIdx, delays.length - 1)]);
            } catch (InterruptedException e) { return; }
            backoffIdx++;
            if (!manualClose) connectIfNeeded();
        }, "supa-rt-reconnect");
        reconnectThread.start();
    }

    private void closeInternal() {
        manualClose = true;
        stopHeartbeat();
        if (reconnectThread != null) reconnectThread.interrupt();
        if (client != null) {
            try { client.close(); } catch (Exception ignored) { }
            client = null;
        }
        joined = false;
    }

    // ---------------- WebSocket 实现 ----------------
    private class WsClient extends WebSocketClient {
        private final String token;

        WsClient(URI uri, Map<String, String> headers, String token) {
            super(uri, new Draft_6455(), headers, 15_000);
            this.token = token;
            setConnectionLostTimeout(60);
        }

        @Override public void onOpen(ServerHandshake handshake) {
            backoffIdx = 0;
            sendJoin(token);
            startHeartbeat();
        }

        @Override public void onMessage(String message) {
            try {
                JSONObject msg = new JSONObject(message);
                String topic = msg.optString("topic", "");
                String event = msg.optString("event", "");
                JSONObject payload = msg.optJSONObject("payload");
                if (payload == null) payload = new JSONObject();
                if ("phx_reply".equals(event) && TOPIC.equals(topic)) {
                    JSONObject resp = payload.optJSONObject("response");
                    if (resp != null && resp.has("postgres_changes")) {
                        JSONArray arr = resp.optJSONArray("postgres_changes");
                        synchronized (bindings) {
                            bindings.clear();
                            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                                JSONObject b = arr.getJSONObject(i);
                                bindings.put(b.optLong("id", -1), b.optString("table", ""));
                            }
                        }
                        joined = true;
                        listener.onJoined();
                    }
                } else if ("postgres_changes".equals(event) && TOPIC.equals(topic)) {
                    JSONArray ids = payload.optJSONArray("ids");
                    JSONObject data = payload.optJSONObject("data");
                    if (ids == null || data == null) return;
                    String table = null;
                    synchronized (bindings) {
                        for (int i = 0; i < ids.length(); i++) {
                            String t = bindings.get(ids.optLong(i, -1));
                            if (t != null) { table = t; break; }
                        }
                    }
                    if (table == null) table = data.optString("table", "");
                    JSONObject record = data.optJSONObject("record");
                    // DELETE 事件的 record 为空，主键在 old_record 里
                    if (record == null || record.length() == 0) record = data.optJSONObject("old_record");
                    String type = data.optString("type", "");
                    if ("tasks".equals(table) && record != null) {
                        listener.onTaskRow(record, type);
                    } else if ("user_state".equals(table) && record != null) {
                        listener.onUserStateRow(record);
                    }
                } else if ("phx_error".equals(event) || "phx_close".equals(event)) {
                    joined = false;
                    close();
                    scheduleReconnect();
                }
            } catch (Exception ignored) { }
        }

        @Override public void onClose(int code, String reason, boolean remote) {
            joined = false;
            stopHeartbeat();
            if (!manualClose) scheduleReconnect();
        }

        @Override public void onError(Exception ex) {
            joined = false;
            if (!manualClose) scheduleReconnect();
        }
    }
}
