package com.auction.model.Entity;

public class Bidder extends User {
    public Bidder(String username, String password, String email) {
        super(username, password, email, Role.BIDDER);
    }
}
