package main.java.com.auction.model;

import java.util.UUID;

public abstract class Entity {
    protected String id;

    public Entity() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }
}