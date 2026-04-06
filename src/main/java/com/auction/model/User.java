package com.auction.model;

public abstract class User extends Entity {

    private static final long serialVersionUID = 1L;

    private String username; //tên tài khoản
    private String password; //mật khẩu
    private Role role; //vai trò(BIDDER/SELLER/ADMIN)
    private UserStatus userStatus; //trạng thái của tài khoản(ACTIVE/BANNED)

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
        return "User{" +
                "username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", role=" + role +
                ", userStatus=" + userStatus +
                '}';
    }
}
