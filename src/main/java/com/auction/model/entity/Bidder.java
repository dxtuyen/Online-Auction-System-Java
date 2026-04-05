package com.auction.model.entity;

public class Bidder extends User {
    private double balance;

    public Bidder(String id, String username, String password, String email, double balance){
        super(id, username , password, email);
        this.balance = balance;
    }

    @Override
    public String getRole() { return "Bidder";}

    public double getBalance(){ return balance;}

    // phương thức khấu trừ tài khoảng, ném ra lỗi để băt nếu số dư không đủ
    public void deductBalance( double amount) {
        if (amount > balance){
            throw new IllegalArgumentException("Lỗi số dư không đủ");
        }
        this.balance -= balance;
    }
    // Phương thức nạp tiền
    public void addBalance(double amount){
        this.balance += amount;
    }
}