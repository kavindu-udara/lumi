package com.example.lumi.lib;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;

import okhttp3.OkHttpClient;
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

        Response response = new OkHttpClient().newCall(request).execute();
        Gson gson = new Gson();
        JsonObject responseObj = gson.fromJson(response.body().string(), JsonObject.class);
        return responseObj;
    }

    public static JsonObject GET(String endpoint, @Nullable String token) throws IOException, IllegalStateException {

        Request request;

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

        Response response = new OkHttpClient().newCall(request).execute();
        Gson gson = new Gson();
        JsonObject responseObj = gson.fromJson(response.body().string(), JsonObject.class);
        return responseObj;
    }
}


