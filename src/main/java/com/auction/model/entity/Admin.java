package com.auction.model.entity;

import com.auction.model.enums.Role;

public class Admin extends User implements canManage {

    private static final long serialVersionUID = 1L;

    public Admin() {
        super();
    }

    public Admin(String username, String password, double totalRevenue) {
        super(username, password, Role.ADMIN);
    }

    public Admin(int id, String username, String password, double totalRevenue) {
        super(id, username, password, Role.ADMIN);
    }

    //Methods
    @Override
    public String toString() {
        return "Admin{" +
                "username=" + super.getUsername() +
                '}';
    }
}
