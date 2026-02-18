package com.example.lumi.models;

import java.io.Serializable;

public class Image implements Serializable {

    private String id;
    private String url;

    private ImageMetaData metaData;

    public Image(String id){
        this.id = id;
    }

    public Image(String id, String url) {
        this.id = id;
        this.url = url;
    }
    public Image(String id, String url, ImageMetaData metaData) {
        this.id = id;
        this.url = url;
        this.metaData = metaData;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public ImageMetaData getMetaData() {
        return metaData;
    }

    public void setMetaData(ImageMetaData metaData) {
        this.metaData = metaData;
    }

}
