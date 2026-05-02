package com.auction.model.entity;

import com.auction.model.enums.Role;
import com.auction.model.enums.UserStatus;

public class User extends Entity {

    private static final long serialVersionUID = 1L;

    private String username; //tên tài khoản
    private String password; //mật khẩu
    private Role role; //vai trò(BIDDER/SELLER/ADMIN)
    private UserStatus userStatus; //trạng thái của tài khoản(ACTIVE/BANNED)
    private double balance;
    private double revenue;

    public User() {
        super();
    }

    public User(String username, String password, Role role, double balance, double revenue) {
        super();
        this.username = username;
        this.password = password;
        this.role = role;
        this.userStatus = UserStatus.ACTIVE;
        this.balance = balance;
        this.revenue = revenue;
    }

    public User(int id, String username, String password, Role role, double balance, double revenue) {
        super(id);
        this.username = username;
        this.password = password;
        this.role = role;
        this.userStatus = UserStatus.ACTIVE;
        this.balance = balance;
        this.revenue = revenue;
    }

    //Getter & Setter
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * @deprecated dùng {@link #setUsername(String)} đúng JavaBean convention.
     */
    @Deprecated
    public void setUserName(String username) {
        setUsername(username);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public UserStatus getUserStatus() {
        return userStatus;
    }

    public void setUserStatus(UserStatus userStatus) {
        this.userStatus = userStatus;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public double getRevenue() {
        return revenue;
    }

    public void setRevenue(double revenue) {
        this.revenue = revenue;
    }

    //Methods
    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", role=" + role +
                ", userStatus=" + userStatus +
                '}';
    }
}

/*
package com.auction.model.entity;

import com.auction.model.enums.Role;

public class Seller1 extends User {

    private static final long serialVersionUID = 1L;

    private double totalRevenue; //doanh thu

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
        return "Seller{" +
            "totalRevenue=" + totalRevenue +
            '}';
    }
}

package com.auction.model.entity;

import com.auction.model.enums.Role;

public class Bidder1 extends User {

    private static final long serialVersionUID = 1L;

    private double balance; //số dư

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

    //Methods
    @Override
    public String toString() {
        return "Bidder{" +
            "balance=" + balance +
            '}';
    }
}

package com.auction.model.entity;

import com.auction.model.enums.Role;

public class Admin1 extends User {

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
*/

