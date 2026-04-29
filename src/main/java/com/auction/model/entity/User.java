package com.auction.model.entity;

import com.auction.model.enums.Role;
import com.auction.model.enums.UserStatus;

public abstract class User extends Entity {

    private static final long serialVersionUID = 1L;

    protected String username; // ten tai khoan
    protected String password; //hashed
    private String email;
    private String fullName; // ten nguoi dung
    protected UserStatus userStatus;
    protected final Role role;

    // không chấp nhận constructor rỗng vì ... (hỏi Giang)
//    protected User() {
//        super();
//        this.userStatus = UserStatus.ACTIVE;
//    }

    protected User(String username, String password, String email,
                   String fullName, Role role) {
        super();
        this.username = username;
        this.password = password;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.userStatus = UserStatus.ACTIVE;
    }
    //mỗi loại user tự quyết định trả về role nào
    public Role getRole() {
        return role;
    }

    //xem xét xem có nên thêm không
//    protected void setRole(Role role) {
//        this.role = role;
//    }

    // Status
    public UserStatus getUserStatus() {
        return userStatus;
    }
    public void setUserStatus(UserStatus userStatus) {
        this.userStatus = userStatus;
        markUpdated();
    }

    // Getter & Setter
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; markUpdated(); }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; markUpdated(); }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; markUpdated(); }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; markUpdated(); }

    // mỗi loại user sẽ cài đặt khác nhau
    public abstract boolean canBid();
    public abstract boolean canSell();
    public abstract boolean canManageSystem();

    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", role=" + getRole() +
                ", userStatus=" + userStatus +
                '}';
    }
}
