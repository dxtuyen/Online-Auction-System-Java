package com.auction.model;

import java.time.LocalDate;

public abstract class User extends Entity {

    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
    private Role role;

    public User() {
        super();
    }

    public User(String username, String password, Role role) {
        super();
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public User(int id, String username, String password, Role role) {
        super(id);
        this.username = username;
        this.password = password;
        this.role = role;
    }

    //Getter & Setter
    public String getUsername() {
        return username;
    }

    public void setUserName(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    //Methods
    @Override
    public String toString() {
        return "Username: " + username +
                " | Password: " + password +
                " | Role: " + role.getDisplayRole();
    }
}
