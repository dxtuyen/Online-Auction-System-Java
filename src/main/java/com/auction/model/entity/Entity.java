package com.auction.model.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public abstract class Entity implements Serializable {

    private static final long serialVersionUID = 1L;

    // thuộc tính theo dõi,
    private final UUID id; //id để định danh obj
    private final LocalDateTime createdAt; // thời điểm tạo obj
    private LocalDateTime updatedAt; // thời điểm cập nhật obj cuối

    //tạo obj mới
    protected Entity() {
        this.id = UUID.randomUUID();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    // load lại dữ liệu từ database, có RỦI RO NULL (đã fix bên dưới)
    protected Entity(UUID id, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    // gọi method này mỗi khi entity bị thay đổi
    protected void markUpdated() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getter

    public UUID getId() {
        return id;
    }

    // chỉ có get, không set vì thời điểm tạo obj không nên bị thay đổi
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // tương tự như trên
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Entity entity = (Entity) o;
        return id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "{id=" + id +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                "}";
    }
}

