package com.auction.model.entity;

import com.auction.model.enums.Role;
import com.auction.model.enums.UserStatus;

public abstract class User extends Entity {

    private static final long serialVersionUID = 1L;

    protected String username;
    protected String password;
    protected UserStatus userStatus;

    public User() {
        super();
        this.userStatus = UserStatus.ACTIVE;
    }

    public User(String username, String password) {
        super();
        this.username = username;
        this.password = password;
        this.userStatus = UserStatus.ACTIVE;
    }

    public User(int id, String username, String password) {
        super(id);
        this.username = username;
        this.password = password;
        this.userStatus = UserStatus.ACTIVE;
    }

    public abstract Role getRole();

    // Getter & Setter
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public UserStatus getUserStatus() {
        return userStatus;
    }

    public void setUserStatus(UserStatus userStatus) {
        this.userStatus = userStatus;
    }

    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", role=" + getRole() +
                ", userStatus=" + userStatus +
                '}';
    }
}
