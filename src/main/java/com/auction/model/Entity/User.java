package com.auction.model.Entity;

public abstract class User {

    private static int userId = 0;
    private String username;
    private String password;//Hash
    private String email;
    private Role role;

    //Constructor
    public User(String username, String password, String email, Role role) {
        User.userId++;
        this.username = username;
        this.password = password;
        this.email = email;
        this.role = role;
    }

    public User() {
    }

    //Getter & Setter
    public String getUsername() {
        return username;
    }

    public void setUsername(String newUsername) {
        this.username = newUsername;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String newPassword) {
        this.password = newPassword;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String newEmail) {
        this.email = newEmail;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role newRole) {
        this.role = newRole;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + userId +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", password='" + password + '\'' +
                ", role=" + role + '\'' +
                '}';
    }
}
