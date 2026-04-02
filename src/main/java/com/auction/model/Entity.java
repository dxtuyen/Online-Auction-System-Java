package com.auction.model;

import java.io.Serializable;
import java.util.UUID;

public abstract class Entity implements Serializable {

    private static final long serialVersionUID = 1L;

    protected int id;

    public Entity() {
    }

    public Entity(int id) {
        this.id = id;
    }

    //Getter & Setter
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
