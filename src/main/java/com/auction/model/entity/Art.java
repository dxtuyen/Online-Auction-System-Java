package com.auction.model.entity;

import com.auction.model.enums.ItemCategory;
import com.auction.model.enums.ItemCondition;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Tác phẩm nghệ thuật - có tác giả, năm sáng tác, chất liệu. */
public class Art extends Item {

    private static final long serialVersionUID = 1L;

    private final String artist;
    private final Integer yearCreated;   // có thể null nếu không rõ
    private final String medium;          // chất liệu: oil/acrylic/watercolor...

    public Art(String name, String description, UUID sellerId,
               BigDecimal startingPrice, List<String> images,
               ItemCondition condition,
               String artist, Integer yearCreated, String medium) {
        super(name, description, sellerId, startingPrice, images,
                ItemCategory.ART, condition);
        this.artist = artist;
        this.yearCreated = yearCreated;
        this.medium = medium;
    }

    public String getArtist() { return artist; }
    public Integer getYearCreated() { return yearCreated; }
    public String getMedium() { return medium; }

    @Override
    public String getSpecificInfo() {
        return String.format("Tác giả: %s | Năm: %s | Chất liệu: %s",
                artist,
                yearCreated == null ? "?" : yearCreated.toString(),
                medium);
    }
}