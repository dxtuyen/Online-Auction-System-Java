package com.auction.model.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public abstract class Entity implements Serializable {

    private static final long serialVersionUID = 1L;

    //thuộc tính theo dõi,
    private String id; //id để định danh obj
    private LocalDateTime createdAt; // thời điểm tạo obj
    private LocalDateTime updatedAt; // thời điểm cập nhật obj cuối

    //tạo obj mới
    protected Entity() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    //load lại dữ liệu từ database
    protected Entity(String id) {
        this.id = id;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Encapsulation: private fields + getter/setter
    public String getId() {
        return id;
    }

    // id không nên thay đổi được, mức truy cập đổi thành protected, hoặc sẽ xóa
    protected void setId(String id) {
        this.id = id;
    }

    //chỉ có get, không set vì thời điểm tạo obj không nên bị thay đổi
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    //tương tự như trên
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    protected void markUpdated() {
        this.updatedAt = LocalDateTime.now();
    }

    public abstract String getDisplayInfo(); // mỗi class sẽ tự cài đặt cách hiển thị thông tin

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Entity entity = (Entity) o;
        return Objects.equals(id, entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id='" + id + "'}";
    }
}

