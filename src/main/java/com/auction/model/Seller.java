package com.auction.model;

public class Seller extends User implements canSell{

    private static final long serialVersionUID = 1L;

    private double totalRevenue;

    public Seller() {
        super();
    }

    public Seller(String username, String password, double totalRevenue) {
        super(username, password, Role.SELLER);
        this.totalRevenue = totalRevenue;
    }

    public Seller(int id, String username, String password, double totalRevenue) {
        super(id, username, password, Role.SELLER);
        this.totalRevenue = totalRevenue;
    }

    //Getter & Setter

    public double getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(double totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    //Methods
    @Override
    public String toString() {
        return "Seller: " + super.getUsername() +
                " | Doanh thu: " + totalRevenue;
    }
}
