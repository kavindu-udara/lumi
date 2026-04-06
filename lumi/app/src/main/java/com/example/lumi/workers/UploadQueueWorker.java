package com.example.lumi.workers;

import android.content.Context;
import android.util.Log;

import com.example.lumi.lib.API;
import com.example.lumi.lib.UploadQueueStore;
import com.example.lumi.models.QueuedUploadItem;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class UploadQueueWorker {

    private static final Object LOCK = new Object();
    private static boolean isRunning = false;

    public static void enqueue(Context context, String firebaseUserId, String authToken) {
        if (context == null
                || firebaseUserId == null || firebaseUserId.trim().isEmpty()
                || authToken == null || authToken.trim().isEmpty()) {
            return;
        }

        synchronized (LOCK) {
            if (isRunning) {
                return;
            }
            isRunning = true;
        }

        Context appContext = context.getApplicationContext();
        new Thread(() -> processQueue(appContext, firebaseUserId, authToken), "lumi-upload-queue").start();
    }

    private static void processQueue(Context context, String firebaseUserId, String authToken) {
        UploadQueueStore store = new UploadQueueStore(context);
        try {
            while (true) {
                List<QueuedUploadItem> items = store.getAll();
                if (items.isEmpty()) {
                    return;
                }

                QueuedUploadItem item = items.get(0);
                File imageFile = new File(item.getFilePath());
                if (!imageFile.exists()) {
                    store.removeById(item.getId());
                    continue;
                }

                JsonObject metadata = new JsonObject();
                metadata.addProperty("capturedAt", String.valueOf(item.getCreatedAt()));
                metadata.addProperty("date", item.getDate());
                metadata.addProperty("firebaseUserId", item.getFirebaseUserId());
                metadata.addProperty("storagePath", item.getFilePath());
                if (item.getLatitude() != null) {
                    metadata.addProperty("latitude", item.getLatitude());
                }
                if (item.getLongitude() != null) {
                    metadata.addProperty("longitude", item.getLongitude());
                }

                boolean success;
                try {
                    success = API.uploadImage("/photos/upload", authToken, imageFile, metadata);
                    Log.i("UploadQueueWorker", "Upload result for " + imageFile.getAbsolutePath() + ": " + success);
                } catch (IOException ex) {
                    return;
                }

                if (!success) {
                    return;
                }

                store.removeById(item.getId());
                if (!imageFile.delete()) {
                    Log.w("UploadQueueWorker", "Uploaded image but failed to delete local file: " + imageFile.getAbsolutePath());
                }
            }
        } finally {
            synchronized (LOCK) {
                isRunning = false;
            }
        }
    }
}


