package com.auction.model.entity;

import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;

import java.util.List;
import java.util.Map;

public class ItemFactory {

    public static Item createItem(ItemCategory category, String name,
                                  String description, double startingPrice,
                                  int sellerId, List<String> images, ItemCondition condition,
                                  Map<String, String> specificAttributes) {
        Item item;
        switch (category) {
            case VEHICLE -> {
                Vehicle v = new Vehicle();
                if (specificAttributes != null) {
                    v.setBrand(specificAttributes.getOrDefault("brand", "Unknown"));
                    v.setModel(specificAttributes.getOrDefault("model", "Unknown"));
                    v.setManufactureYear(parseIntSafe(specificAttributes.get("manufactureYear"), 0));
                    v.setMileage(parseIntSafe(specificAttributes.get("mileage"), 0));
                    v.setColor(specificAttributes.getOrDefault("color", "Unknown"));
                    v.setFuelType(specificAttributes.getOrDefault("fuelType", "Unknown"));
                    v.setTransmission(specificAttributes.getOrDefault("transmission", "Unknown"));
                    v.setOwnerCount(parseIntSafe(specificAttributes.get("ownerCount"), 0));
                    String hasRegistration = specificAttributes.getOrDefault("hasRegistration", "false");
                    v.setHasRegistration(Boolean.parseBoolean(hasRegistration));
                }
                item = v;
            }
            case ELECTRONICS -> {
                Electronic e = new Electronic();
                if (specificAttributes != null) {
                    e.setBrand(specificAttributes.getOrDefault("brand", "Unknown"));
                    e.setModel(specificAttributes.getOrDefault("model", "Unknown"));
                    String warrantyStr = specificAttributes.getOrDefault("warrantyMonths", "0");
                    e.setWarrantyMonths(Integer.parseInt(warrantyStr));
                }
                item = e;
            }
            case ART -> {
                Art a = new Art();
                if (specificAttributes != null) {
                    a.setArtist(specificAttributes.getOrDefault("artist", "Unknown"));
                    String creationYear = specificAttributes.getOrDefault("year", "0");
                    a.setYear(Integer.parseInt(creationYear));
                }
                item = a;
            }
            default -> {
                OtherItem def = new OtherItem();
                def.setExtraDetails(specificAttributes);
                item = def;
            }
        }

        item.setName(name);
        item.setDescription(description);
        item.setStartingPrice(startingPrice);
        item.setSellerId(sellerId);
        item.setImages(images);
        item.setCondition(condition);
        item.setCategory(category);

        return item;
    }

    public static Item createItemFromDB(int id, ItemCategory category, String name,
                                         String description, double startingPrice,
                                         int sellerId, List<String> images, ItemCondition condition,
                                         Map<String, String> specificAttributes) {
        Item item = createItem(category, name, description, startingPrice,
                sellerId, images, condition, specificAttributes);
        item.setId(id);
        return item;
    }

    private static int parseIntSafe(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}