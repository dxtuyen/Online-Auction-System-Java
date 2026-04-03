package com.auction.model;

public class Admin extends User implements canManage {

    private static final long serialVersionUID = 1L;

    private double totalRevenue;

    public Admin() {
        super();
    }

    public Admin(String username, String password, double totalRevenue) {
        super(username, password, Role.ADMIN);
        this.totalRevenue = totalRevenue;
    }

    public Admin(int id, String username, String password, double totalRevenue) {
        super(id, username, password, Role.ADMIN);
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
        return "Admin: " + super.getUsername() +
                " | Doanh thu: " + totalRevenue;
    }
    
}
