package com.auction.model;

import java.util.List;

public class Art extends Item {

    private static final long serialVersionUID = 1L;

    private String artist;
    private int year;

    public Art() {
        super();
    }

    public Art(String name, String description, int sellerId, double startingPrice, List<String> images, ItemCategory category, ItemCondition condition, String artist, int year) {
        super(name, description, sellerId, startingPrice, images, category, condition);
        this.artist = artist;
        this.year = year;
    }

    public Art(int id, String name, String description, int sellerId, double startingPrice, List<String> images, ItemCategory category, ItemCondition condition, String artist, int year) {
        super(id, name, description, sellerId, startingPrice, images, category, condition);
        this.artist = artist;
        this.year = year;
    }

    //Getter & Setter
    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public int year() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }
}
