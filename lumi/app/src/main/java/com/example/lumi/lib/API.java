package com.example.lumi.lib;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.File;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Request;
import okhttp3.Response;

public class API {

    final static String BASE_URL = "http://172.20.10.2:3000/api/v1";
    OkHttpClient client;

    public API() {
        this.client = new OkHttpClient();
    }

    public String getBaseUrl() {
        return BASE_URL;
    }

    public static JsonObject POST(String endpoint, JsonObject reqObj) throws IOException, IllegalStateException {
//        send a POST request to the API and return the response as a string
        Request request = new Request.Builder()
                .url(BASE_URL + endpoint)
                .post(okhttp3.RequestBody.create(reqObj.toString(), okhttp3.MediaType.parse("application/json")))
                .build();

        try (Response response = new OkHttpClient().newCall(request).execute()) {
            Gson gson = new Gson();
            return gson.fromJson(response.body().string(), JsonObject.class);
        }
    }

    public static JsonObject POST(String endpoint, @Nullable String token, JsonObject reqObj) throws IOException, IllegalStateException {
        Request.Builder requestBuilder = new Request.Builder()
                .url(BASE_URL + endpoint)
                .post(okhttp3.RequestBody.create(reqObj.toString(), okhttp3.MediaType.parse("application/json")));

        if (token != null) {
            requestBuilder.addHeader("Authorization", "Bearer " + token);
        }

        try (Response response = new OkHttpClient().newCall(requestBuilder.build()).execute()) {
            Gson gson = new Gson();
            String responseBody = response.body() != null ? response.body().string() : "";
            Log.i("API", "POST response code: " + response.code() + ", message: " + response.message() + ", body: " + responseBody);
            return gson.fromJson(responseBody, JsonObject.class);
        }
    }

    public static JsonElement PUT(String endpoint, @Nullable String token, JsonObject reqObj) throws IOException, IllegalStateException {
        Request.Builder requestBuilder = new Request.Builder()
                .url(BASE_URL + endpoint)
                .put(okhttp3.RequestBody.create(reqObj.toString(), okhttp3.MediaType.parse("application/json")));

        if (token != null) {
            requestBuilder.addHeader("Authorization", "Bearer " + token);
        }

        try (Response response = new OkHttpClient().newCall(requestBuilder.build()).execute()) {
            Gson gson = new Gson();
            String responseBody = response.body() != null ? response.body().string() : "";
            Log.i("API", "PUT response code: " + response.code() + ", message: " + response.message() + ", body: " + responseBody);
            return gson.fromJson(responseBody, JsonElement.class);
        }
    }

    public static JsonElement GET(String endpoint, @Nullable String token) throws IOException, IllegalStateException {

        Request request;
        Log.i("API", "GET request to: " + BASE_URL + endpoint + ", with token: " + token);

        if (token == null) {
            request = new Request.Builder()
                    .url(BASE_URL + endpoint)
                    .get()
                    .build();
        } else {
            request = new Request.Builder()
                    .addHeader("Authorization", "Bearer " + token)
                    .url(BASE_URL + endpoint)
                    .get()
                    .build();
        }

        try (Response response = new OkHttpClient().newCall(request).execute()) {
            Gson gson = new Gson();
            String responseBody = response.body() != null ? response.body().string() : "";
            Log.i("API", "GET response code: " + response.code() + ", message: " + response.message() + ", body: " + responseBody);
            return gson.fromJson(responseBody, JsonElement.class);
        }
    }

    public static JsonElement DELETE(String endpoint, @Nullable String token) throws IOException, IllegalStateException {
        Request.Builder requestBuilder = new Request.Builder()
                .url(BASE_URL + endpoint)
                .delete();

        if (token != null) {
            requestBuilder.addHeader("Authorization", "Bearer " + token);
        }

        try (Response response = new OkHttpClient().newCall(requestBuilder.build()).execute()) {
            Gson gson = new Gson();
            String responseBody = response.body() != null ? response.body().string() : "";
            Log.i("API", "DELETE response code: " + response.code() + ", message: " + response.message() + ", body: " + responseBody);
            return gson.fromJson(responseBody, JsonElement.class);
        }
    }

    public static JsonElement DELETE(String endpoint, @Nullable String token, JsonObject reqObj) throws IOException, IllegalStateException {
        RequestBody body = RequestBody.create(reqObj.toString(), MediaType.parse("application/json"));
        Request.Builder requestBuilder = new Request.Builder()
                .url(BASE_URL + endpoint)
                .method("DELETE", body);

        if (token != null) {
            requestBuilder.addHeader("Authorization", "Bearer " + token);
        }

        try (Response response = new OkHttpClient().newCall(requestBuilder.build()).execute()) {
            Gson gson = new Gson();

            String responseBody = response.body() != null ? response.body().string() : "";
            Log.i("API", "DELETE(body) response code: " + response.code() + ", message: " + response.message() + ", body: " + responseBody);
            return gson.fromJson(responseBody, JsonElement.class);
        }
    }

    public static boolean uploadImage(String endpoint, @Nullable String token, File imageFile, JsonObject metadata) throws IOException {
        String contentType = "image/jpeg";
        String fileName = imageFile.getName().toLowerCase();
        if (fileName.endsWith(".mp4")) {
            contentType = "video/mp4";
        } else if (fileName.endsWith(".mov")) {
            contentType = "video/quicktime";
        }

        MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                        "image",
                        imageFile.getName(),
                        RequestBody.create(imageFile, MediaType.parse(contentType))
                );

        if (metadata != null) {
            for (String key : metadata.keySet()) {
                if (metadata.get(key) instanceof JsonPrimitive) {
                    multipartBuilder.addFormDataPart(key, metadata.get(key).getAsString());
                }
            }
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(BASE_URL + endpoint)
                .post(multipartBuilder.build());

        if (token != null) {
            requestBuilder.addHeader("Authorization", "Bearer " + token);
        }

        try (Response response = new OkHttpClient().newCall(requestBuilder.build()).execute()) {
            Log.i("API", "Upload response code: " + response.code() + ", message: " + response.message());
            return response.isSuccessful();
        }
    }
}


