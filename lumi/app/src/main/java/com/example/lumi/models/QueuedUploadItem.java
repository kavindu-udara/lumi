package com.example.lumi.models;

public class QueuedUploadItem {

    private String id;
    private String filePath;
    private String date;
    private long createdAt;
    private Double latitude;
    private Double longitude;
    private String firebaseUserId;

    public QueuedUploadItem() {
        // Required for Gson deserialization.
    }

    public QueuedUploadItem(
            String id,
            String filePath,
            String date,
            long createdAt,
            Double latitude,
            Double longitude,
            String firebaseUserId
    ) {
        this.id = id;
        this.filePath = filePath;
        this.date = date;
        this.createdAt = createdAt;
        this.latitude = latitude;
        this.longitude = longitude;
        this.firebaseUserId = firebaseUserId;
    }

    public String getId() {
        return id;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getDate() {
        return date;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public String getFirebaseUserId() {
        return firebaseUserId;
    }
}

