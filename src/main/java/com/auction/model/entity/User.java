package com.auction.model.entity;

import com.auction.model.enums.Role;
import com.auction.model.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public abstract class User extends Entity {

    private static final long serialVersionUID = 1L;

    private String username; // ten tai khoan
    protected String password; // hashed
    private String email;
    private String fullName; // ten nguoi dung
    protected UserStatus userStatus;
    protected final Role role;

    protected User(String username, String password, String email,
                   String fullName, Role role) {
        super();
        this.username = Objects.requireNonNull(username);
        this.password = Objects.requireNonNull(password);
        this.email = Objects.requireNonNull(email);
        this.fullName = Objects.requireNonNull(fullName);
        this.role = Objects.requireNonNull(role);
        this.userStatus = UserStatus.ACTIVE;
    }

    // Load từ DB
    protected User(UUID id,
                   LocalDateTime createdAt,
                   LocalDateTime updatedAt,
                   String username,
                   String password,
                   String email,
                   String fullName,
                   Role role,
                   UserStatus status) {

        super(id, createdAt, updatedAt);

        this.username = Objects.requireNonNull(username, "username must not be null");
        this.password = Objects.requireNonNull(password, "password must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.fullName = Objects.requireNonNull(fullName, "fullName must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.userStatus = Objects.requireNonNull(status, "status must not be null");
    }

    //mỗi loại user tự quyết định trả về role nào
    public Role getRole() {
        return role;
    }

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
    public void setUsername(String username) {
        this.username = Objects.requireNonNull(username);
        markUpdated();
    }
    public String getPassword() { return password; }
    public void setHashedPassword(String password) {
        this.password = Objects.requireNonNull(password);
        markUpdated();
    }

    public String getEmail() { return email; }
    public void setEmail(String email) {
        this.email = Objects.requireNonNull(email);
        markUpdated();
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) {
        this.fullName = Objects.requireNonNull(fullName);
        markUpdated();
    }

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
