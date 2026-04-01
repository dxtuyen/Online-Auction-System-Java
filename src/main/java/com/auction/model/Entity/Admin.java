package com.auction.model.Entity;

public class Admin extends User {
    public Admin(String username, String password, String email) {
        super(username, password, email, Role.ADMIN);
    }
}
