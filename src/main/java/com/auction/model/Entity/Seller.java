package com.auction.model.Entity;

public class Seller extends User{
    public Seller(String username, String password, String email) {
        super(username, password, email, Role.SELLER);
    }
}
