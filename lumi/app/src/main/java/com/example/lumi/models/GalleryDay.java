package com.example.lumi.models;

import java.util.List;

public class GalleryDay {

    private String date;
    private List<String> photos;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public List<String> getPhotos() {
        return photos;
    }

    public void setPhotos(List<String> photos) {
        this.photos = photos;
    }

    public int getPhotoCount() {
        return photos == null ? 0 : photos.size();
    }
}

