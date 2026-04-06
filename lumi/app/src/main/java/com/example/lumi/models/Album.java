package com.example.lumi.models;

public class Album {
    private final String id;
    private final String name;

    public Album(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}

