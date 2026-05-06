package com.auction.model.entity.profile;

import com.auction.model.entity.User;
import com.auction.model.enums.Role;

import java.util.Objects;

public class AdminProfile implements RoleProfile {
    private static final long serialVersionUID = 1L;

    /**
     * transient để tránh vòng lặp serialize User -> AdminProfile -> User.
     * Sau khi deserialize, phải gọi attachOwner() lại trước khi dùng các method ban/unban/grant.
     */
    private transient User owner;

    @Override
    public Role getRole() {
        return Role.ADMIN;
    }

    public void attachOwner(User owner) {
        if (this.owner != null && this.owner != owner) {
            throw new IllegalStateException("AdminProfile đã thuộc về user khác");
        }
        this.owner = Objects.requireNonNull(owner);
    }

    /**
     * KHOA USER KHAC
     */
    public void banUser(User target) {
        Objects.requireNonNull(target, "Target khong the la null");
        ensureOwnerActive();
        if (target.equals(owner)) {
            throw new IllegalArgumentException("Admin khong the tu khoa chinh minh");
        }
        target.ban();
    }

    /**
     * UNBAN USER
     */
    public void unbanUser(User target) {
        Objects.requireNonNull(target, "Target khong the la null");
        ensureOwnerActive();
        target.activate();
    }

    /**
     * CAP ROLE CHO USER
     */
    public void grantRoleTo(User target, RoleProfile newProfile) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(newProfile, "newProfile must not be null");
        ensureOwnerActive();
        if (newProfile instanceof AdminProfile) {
            // Cố tình block: không cho admin tự cấp profile của mình cho người khác.
            // Nếu muốn cấp Admin, phải tạo AdminProfile MỚI cho target.
            throw new IllegalArgumentException(
                    "Phải truyền AdminProfile MỚI, không phải instance đã có owner");
        }
        target.grantRole(newProfile);
    }

    private void ensureOwnerActive() {
        if (owner == null) {
            throw new IllegalStateException(
                    "AdminProfile chưa được gắn với User (chưa qua User.grantRole)");
        }
        if (!owner.isActive()) {
            throw new IllegalStateException(
                    "Admin '" + owner.getUsername() + "' đang không active, không thể thao tác");
        }
    }

    @Override
    public String toString() {
        return "AdminProfile{owner=" + (owner == null ? "null" : owner.getUsername()) + "}";
    }
}
