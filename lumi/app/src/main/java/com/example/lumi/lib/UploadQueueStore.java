package com.example.lumi.lib;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.lumi.models.QueuedUploadItem;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UploadQueueStore {

    private static final String PREF_NAME = "upload_queue_store";
    private static final String KEY_QUEUE = "upload_queue_items";

    private final SharedPreferences prefs;
    private final Gson gson;
    private final Type listType;

    public UploadQueueStore(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.listType = new TypeToken<List<QueuedUploadItem>>() { }.getType();
    }

    public synchronized void enqueue(QueuedUploadItem item) {
        List<QueuedUploadItem> items = getItemsInternal();
        items.add(item);
        save(items);
    }

    public synchronized List<QueuedUploadItem> getAll() {
        List<QueuedUploadItem> items = getItemsInternal();
        items.sort(Comparator.comparingLong(QueuedUploadItem::getCreatedAt));
        return items;
    }

    public synchronized void removeById(String id) {
        List<QueuedUploadItem> items = getItemsInternal();
        items.removeIf(item -> item.getId().equals(id));
        save(items);
    }

    public synchronized Map<String, Integer> getPendingCountsByDate() {
        Map<String, Integer> counts = new HashMap<>();
        for (QueuedUploadItem item : getItemsInternal()) {
            String date = item.getDate();
            if (date == null || date.trim().isEmpty()) {
                continue;
            }
            Integer current = counts.get(date);
            counts.put(date, (current == null ? 0 : current) + 1);
        }
        return counts;
    }

    private List<QueuedUploadItem> getItemsInternal() {
        String json = prefs.getString(KEY_QUEUE, null);
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<QueuedUploadItem> items = gson.fromJson(json, listType);
        return items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    private void save(List<QueuedUploadItem> items) {
        prefs.edit().putString(KEY_QUEUE, gson.toJson(items, listType)).apply();
    }
}

