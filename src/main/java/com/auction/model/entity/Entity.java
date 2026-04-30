package com.auction.model.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Base class cho tất cả entity trong hệ thống.
 *
 * Áp dụng:
 *  - DDD Identity Equality: 2 entity bằng nhau khi cùng id (không so sánh từng field)
 *  - Template Method: lớp con kế thừa và bổ sung domain logic
 *  - Immutability: id, createdAt là final → không bao giờ đổi sau khi tạo
 */
public abstract class Entity implements Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID id;                 // định danh duy nhất
    private final LocalDateTime createdAt; // bất biến, set 1 lần
    private LocalDateTime updatedAt;       // mutable, đổi mỗi lần markUpdated()

    /** Tạo entity MỚI (chưa có trong DB) */
    protected Entity() {
        this.id = UUID.randomUUID();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /** Restore entity từ DB - bắt buộc non-null để tránh data corrupt */
    protected Entity(UUID id, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * Gọi method này MỖI KHI state của entity thay đổi.
     * Đặt protected để chỉ subclass và package gọi được, ngăn lạm dụng từ ngoài.
     */
    protected void markUpdated() {
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    /**
     * equals/hashCode dựa HOÀN TOÀN trên id (DDD pattern).
     * Đánh dấu final để subclass không thể override sai.
     */
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Entity entity = (Entity) o;
        return id.equals(entity.id);
    }

    @Override
    public final int hashCode() {
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

