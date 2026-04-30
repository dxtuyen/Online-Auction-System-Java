package com.auction.model.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.UUID;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private final UUID id;

    private String username;
    private String password;

    public User(String name, String password) {
        this.id = UUID.randomUUID();
        this.username = name;
        this.password = password;
    }

    public User(UUID id, String name, String password) {
        this.id = id;
        this.username = name;
        this.password = password;
    }

    //Getter
    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
