package com.example.lumi.lib;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.MasterKey;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.security.GeneralSecurityException;

public class SessionManager {

    private static final String PREF_NAME = "lumi_secure_prefs";
    private static final String KEY_TOKEN = "auth_token";
    private static final String KEY_USER = "user_data";

    private SharedPreferences sharedPreferences;
    private Gson gson;

    public  SessionManager(Context context){
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            sharedPreferences = androidx.security.crypto.EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            gson = new Gson();
        }catch (GeneralSecurityException | java.io.IOException e) {
            throw new RuntimeException("Failed to initialize SessionManager", e);
        }
    }

    public  void saveToken(String token) {
        sharedPreferences.edit().putString(KEY_TOKEN, token).apply();
    }

    public  void saveUser(JsonObject user) {
        sharedPreferences.edit().putString(KEY_USER, user.toString()).apply();
    }

    public String getToken() {
        return sharedPreferences.getString(KEY_TOKEN, null);
    }

    public JsonObject getUser() {
        String userJson = sharedPreferences.getString(KEY_USER, null);
        if (userJson != null) {
            return gson.fromJson(userJson, JsonObject.class);
        }
        return null;
    }

    public boolean isLoggedIn(){
        return getToken() != null;
    }

    public void clearSession() {
        sharedPreferences.edit().clear().apply();
    }

}
