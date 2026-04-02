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

    public User(String userName, String password, Role role) {
        super();
        this.username = userName;
        this.password = password;
        this.role = role;
    }

    public User(int id, String userName, String password, Role role) {
        super(id);
        this.username = userName;
        this.password = password;
        this.role = role;
    }

    //Getter & Setter
    public String getUserName() {
        return username;
    }

    public void setUserName(String userName) {
        this.username = userName;
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
        return "Username: " + username + " | Password: " + password + " | Role: " + role.getDisplayRole();
    }
}
