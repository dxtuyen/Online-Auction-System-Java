package com.auction.model.entity;

public abstract class User extends Entity {

    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
    private Role role;
    private UserStatus userStatus;

    public User() {
        super();
    }

    public User(String username, String password, Role role) {
        super();
        this.username = username;
        this.password = password;
        this.role = role;
        this.userStatus = UserStatus.ACTIVE;
    }

    public User(int id, String username, String password, Role role) {
        super(id);
        this.username = username;
        this.password = password;
        this.role = role;
        this.userStatus = UserStatus.ACTIVE;
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

    public UserStatus getUserStatus() {
        return userStatus;
    }

    public void setUserStatus(UserStatus userStatus) {
        this.userStatus = userStatus;
    }

    //Methods
    @Override
    public String toString() {
        return "Tên tài khoản: " + username +
                " | Mật khẩu: " + password +
                " | Role: " + role.getDisplayRole() +
                " | Trạng thái: " + userStatus.getDisplayStatus();
    }
}
