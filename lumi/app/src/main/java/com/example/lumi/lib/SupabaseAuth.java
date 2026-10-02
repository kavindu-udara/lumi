package com.example.lumi.lib;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.lumi.BuildConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SupabaseAuth {
    public interface CallbackResult {
        void onSuccess(JsonObject response);
        void onError(String message);
    }

    private static final MediaType JSON = MediaType.parse("application/json");
    private final OkHttpClient client = new OkHttpClient();
    private final SessionManager sessionManager;

    public SupabaseAuth(Context context) {
        sessionManager = new SessionManager(context.getApplicationContext());
    }

    public void signIn(String email, String password, CallbackResult callback) {
        JsonObject body = credentials(email, password);
        request("/auth/v1/token?grant_type=password", body, callback, true);
    }

    public void signUp(String email, String password, CallbackResult callback) {
        JsonObject body = credentials(email, password);
        request("/auth/v1/signup", body, callback, true);
    }

    public void signOut(CallbackResult callback) {
        Request request = builder("/auth/v1/logout")
                .post(RequestBody.create("{}", JSON))
                .addHeader("Authorization", "Bearer " + sessionManager.getToken())
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                sessionManager.clearSession();
                callback.onError("Could not contact Supabase: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response ignored = response) {
                    if (!response.isSuccessful()) {
                        callback.onError(errorMessage(response));
                        return;
                    }
                    sessionManager.clearSession();
                    callback.onSuccess(new JsonObject());
                }
            }
        });
    }

    private void request(String path, JsonObject body, CallbackResult callback, boolean persistSession) {
        final Request request;
        try {
            request = builder(path)
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();
        } catch (IllegalStateException e) {
            callback.onError("Supabase is not configured. Set SUPABASE_URL and "
                    + "SUPABASE_PUBLISHABLE_KEY in .env, then rebuild the app.");
            return;
        }
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Could not contact Supabase: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response ignored = response) {
                    if (!response.isSuccessful()) {
                        callback.onError(errorMessage(response));
                        return;
                    }
                    String responseBody = response.body() == null ? "{}" : response.body().string();
                    JsonObject result = JsonParser.parseString(responseBody).getAsJsonObject();
                    if (persistSession) {
                        saveSession(result);
                    }
                    callback.onSuccess(result);
                } catch (RuntimeException e) {
                    callback.onError("Supabase returned an invalid response");
                }
            }
        });
    }

    private Request.Builder builder(String path) {
        if (BuildConfig.SUPABASE_URL.isEmpty() || BuildConfig.SUPABASE_PUBLISHABLE_KEY.isEmpty()) {
            throw new IllegalStateException("Supabase configuration is missing");
        }
        return new Request.Builder()
                .url(BuildConfig.SUPABASE_URL + path)
                .addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                .addHeader("Content-Type", "application/json");
    }

    private void saveSession(JsonObject response) {
        if (response.has("access_token") && response.has("refresh_token")) {
            sessionManager.saveToken(response.get("access_token").getAsString());
            sessionManager.saveRefreshToken(response.get("refresh_token").getAsString());
            if (response.has("user") && response.get("user").isJsonObject()) {
                sessionManager.saveUser(response.getAsJsonObject("user"));
            }
        }
    }

    private static JsonObject credentials(String email, String password) {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        return body;
    }

    private static String errorMessage(Response response) throws IOException {
        if (response.body() != null) {
            JsonObject error = JsonParser.parseString(response.body().string()).getAsJsonObject();
            if (error.has("msg")) return error.get("msg").getAsString();
            if (error.has("message")) return error.get("message").getAsString();
        }
        return "Supabase request failed (" + response.code() + ")";
    }
}
