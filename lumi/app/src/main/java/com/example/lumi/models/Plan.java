package com.example.lumi.models;

public class Plan {
    private final String id;
    private final String name;
    private final long storageLimit;
    private final double price;

    public Plan(String id, String name, long storageLimit, double price) {
        this.id = id;
        this.name = name;
        this.storageLimit = storageLimit;
        this.price = price;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getStorageLimit() {
        return storageLimit;
    }

    public double getPrice() {
        return price;
    }
}

