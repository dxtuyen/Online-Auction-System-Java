package com.auction.model;

import java.io.Serializable;

public class Bidder extends User implements Serializable, canBid, canSell {

    private static final long serialVersionUID = 1L;

    private double balance;

    public Bidder() {
        super();
    }

    public Bidder(String username, String password, double balance) {
        super(username, password, Role.BIDDER);
        this.balance = balance;
    }

    public Bidder(int id, String username, String password, double balance) {
        super(id, username, password, Role.BIDDER);
        this.balance = balance;
    }

    //Getter & Setter

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

}
