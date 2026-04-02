package com.auction.model;

public abstract class Entity {
    protected int id;

    public int getId() {
        return id;
    }

    public void setId(int newId) {
        this.id = newId;
    }
}
