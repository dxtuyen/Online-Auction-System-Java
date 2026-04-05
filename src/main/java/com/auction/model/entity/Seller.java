package com.auction.model.entity;

import java.util.ArrayList;
import java.util.List;

public class Seller extends User {
    private List<String> itemIds; // danh sách id của các vật phẩm

    public Seller(String id, String username, String password, String email) {
        super(id, username, password, email);
        this.itemIds = new ArrayList<>();
    }

    @Override
    public String getRole() { return "SELLER"; }

    public List<String> getItemIds() { return new ArrayList<>(itemIds); }

    // phương thức thêm vật vật phẩm vào danh sách
    public void addItem(String itemId) { itemIds.add(itemId); }
}