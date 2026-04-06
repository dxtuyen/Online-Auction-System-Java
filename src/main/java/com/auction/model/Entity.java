package com.auction.model;

import java.io.Serializable;
import java.util.Objects;

public abstract class Entity implements Serializable {

    private static final long serialVersionUID = 1L;

    protected int id; //id

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

    //Methods
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Entity entity = (Entity) o;
        return Objects.equals(id, entity.id);
    }

    @Override
    public String toString() {
        return "Entity{" +
                "id=" + id +
                '}';
    }
}
