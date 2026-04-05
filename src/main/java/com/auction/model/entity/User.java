package com.auction.model.entity;

public abstract class User extends Entity {
    private String username;
    private String password;
    private String email;

    public User(String id, String username, String password, String email) {
        super(id);
        this.username = username;
        this.password = password;
        this.email = email;
    }

    // Getters
    public String getUsername() { return username; }
    public String getEmail() { return email; }

    // Không có getPassword() public → bảo mật
    // Chỉ verify qua method riêng
    public boolean verifyPassword(String input) {
        return this.password.equals(input);
    }

    // tạo phương thức lấy ra vai vai trò của người dùng.
    public abstract String getRole();

    public String getDisplayName() {
        return username;
    }

    @Override
    public String toDisplayString() {
        return String.format("[%s] %s (%s)", getRole(), username, email);
    }
}