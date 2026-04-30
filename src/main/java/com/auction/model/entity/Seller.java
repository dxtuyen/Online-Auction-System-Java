package com.auction.model.entity;

import com.auction.model.enums.Role;
import com.auction.model.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class Seller extends User {

    private static final long serialVersionUID = 1L;

    private double totalRevenue; //doanh thu

    // Tạo seller mới
    public Seller(String username, String hashedPassword, String email, String fullName) {
        super(username, hashedPassword, email, fullName, Role.SELLER);
        this.totalRevenue = 0.0;
    }

    // Tạo seller mới + set doanh thu ban đầu
    public Seller(String username, String hashedPassword, String email, String fullName, double totalRevenue) {
        super(username, hashedPassword, email, fullName, Role.SELLER);
        this.totalRevenue = totalRevenue;
    }

    // Load từ DB
    public Seller(UUID id,
                  LocalDateTime createdAt,
                  LocalDateTime updatedAt,
                  String username,
                  String password,
                  String email,
                  String fullName,
                  UserStatus status,
                  double totalRevenue) {

        super(
                id,
                createdAt,
                updatedAt,
                Objects.requireNonNull(username, "username must not be null"),
                Objects.requireNonNull(password, "password must not be null"),
                Objects.requireNonNull(email, "email must not be null"),
                Objects.requireNonNull(fullName, "fullName must not be null"),
                Role.SELLER,
                Objects.requireNonNull(status, "status must not be null")
        );

        this.totalRevenue = totalRevenue;
    }

    //Getter & Setter
    public double getTotalRevenue() {
        return totalRevenue;
    }

    public void addRevenue(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Revenue must be > 0");
        }
        this.totalRevenue += amount;
        markUpdated();
    }

    @Override
    public boolean canBid() {
        return false;
    }

    @Override
    public boolean canSell() {
        return true;
    }

    @Override
    public boolean canManageSystem() {
        return false;
    }

    @Override
    public String toString() {
        return "Seller{" +
                "username='" + getUsername() + '\'' +
                ", role=" + getRole() +
                ", userStatus=" + getUserStatus() +
                ", totalRevenue=" + totalRevenue +
                '}';
    }
}
