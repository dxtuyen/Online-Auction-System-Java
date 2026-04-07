package com.auction.model.entity;

import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;

import java.util.List;
import java.util.Map;

public class OtherItem extends Item {

    private static final long serialVersionUID = 1L;

    private Map<String, String> extraDetails; //chừa các thông tin khác

    public OtherItem() {
        super();
    }

    public OtherItem(String name, String description, int sellerId, double startingPrice, List<String> images, ItemCategory category, ItemCondition condition, Map<String, String> extraDetails) {
        super(name, description, sellerId, startingPrice, images, category, condition);
        this.extraDetails = extraDetails;
    }

    public OtherItem(int id, String name, String description, int sellerId, double startingPrice, List<String> images, ItemCategory category, ItemCondition condition, Map<String, String> extraDetails) {
        super(id, name, description, sellerId, startingPrice, images, category, condition);
        this.extraDetails = extraDetails;
    }

    //Getter & Setter
    public Map<String, String> getExtraDetails() {
        return extraDetails;
    }

    public void setExtraDetails(Map<String, String> extraDetails) {
        this.extraDetails = extraDetails;
    }

    //Methods
    @Override
    public String toString() {
        return "OtherItem{" +
                "extraDetails=" + extraDetails +
                '}';
    }
}
