package com.example.lumi.lib;

import android.content.Context;
import android.util.Log;

import com.example.lumi.BuildConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class SupabaseNotificationRealtime {
    public interface Listener {
        void onNotification(JsonObject record);
    }

    private final OkHttpClient client = new OkHttpClient();
    private final SessionManager sessionManager;
    private final Listener listener;
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
    private WebSocket socket;

    public SupabaseNotificationRealtime(Context context, Listener listener) {
        this.sessionManager = new SessionManager(context.getApplicationContext());
        this.listener = listener;
    }

    public void connect() {
        closeSocket();
        String token = sessionManager.getToken();
        String userId = sessionManager.getUser() != null && sessionManager.getUser().has("id")
                ? sessionManager.getUser().get("id").getAsString() : null;
        if (token == null || userId == null || BuildConfig.SUPABASE_URL.isEmpty()
                || BuildConfig.SUPABASE_PUBLISHABLE_KEY.isEmpty()) {
            return;
        }

        String websocketUrl = BuildConfig.SUPABASE_URL
                .replaceFirst("^https://", "wss://")
                .replaceFirst("^http://", "ws://")
                + "/realtime/v1/websocket?apikey=" + BuildConfig.SUPABASE_PUBLISHABLE_KEY
                + "&vsn=1.0.0";
        Request request = new Request.Builder().url(websocketUrl).build();
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                JsonObject config = new JsonObject();
                JsonObject postgres = new JsonObject();
                postgres.addProperty("event", "INSERT");
                postgres.addProperty("schema", "public");
                postgres.addProperty("table", "user_notifications");
                postgres.addProperty("filter", "user_id=eq." + userId);
                config.add("broadcast", new JsonObject());
                config.add("presence", new JsonObject());
                config.add("postgres_changes", new com.google.gson.JsonArray());
                config.getAsJsonArray("postgres_changes").add(postgres);

                JsonObject payload = new JsonObject();
                payload.add("config", config);
                payload.addProperty("access_token", token);

                JsonObject join = new JsonObject();
                join.addProperty("topic", "realtime:notifications");
                join.addProperty("event", "phx_join");
                join.add("payload", payload);
                join.addProperty("ref", "1");
                join.addProperty("join_ref", "1");
                webSocket.send(join.toString());

                heartbeat.scheduleAtFixedRate(() -> {
                    if (socket == webSocket) {
                        JsonObject heartbeatMessage = new JsonObject();
                        heartbeatMessage.addProperty("topic", "phoenix");
                        heartbeatMessage.addProperty("event", "heartbeat");
                        heartbeatMessage.add("payload", new JsonObject());
                        heartbeatMessage.addProperty("ref", "heartbeat");
                        webSocket.send(heartbeatMessage.toString());
                    }
                }, 20, 20, TimeUnit.SECONDS);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JsonObject message = JsonParser.parseString(text).getAsJsonObject();
                    String event = message.has("event") ? message.get("event").getAsString() : "";
                    if ("postgres_changes".equals(event)) {
                        JsonObject payload = message.getAsJsonObject("payload");
                        JsonObject data = payload.has("data") && payload.get("data").isJsonObject()
                                ? payload.getAsJsonObject("data") : payload;
                        JsonObject record = data.has("record") && data.get("record").isJsonObject()
                                ? data.getAsJsonObject("record") : null;
                        if (record != null) listener.onNotification(record);
                    } else if ("phx_error".equals(event) || "phx_close".equals(event)) {
                        Log.w("SupabaseRealtime", "Realtime subscription closed: " + text);
                    }
                } catch (RuntimeException error) {
                    Log.w("SupabaseRealtime", "Ignoring malformed Realtime message", error);
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable throwable, Response response) {
                Log.w("SupabaseRealtime", "Realtime connection failed", throwable);
                if (socket == webSocket) socket = null;
            }
        });
    }

    public void close() {
        closeSocket();
    }

    private void closeSocket() {
        if (socket != null) {
            socket.close(1000, "Activity stopped");
            socket = null;
        }
    }
}
