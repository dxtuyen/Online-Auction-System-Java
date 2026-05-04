package com.auction.model.entity;

import com.auction.model.enums.Role;
import com.auction.model.entity.profile.RoleProfile;
import com.auction.model.entity.profile.AdminProfile;
import com.auction.model.entity.profile.BidderProfile;
import com.auction.model.entity.profile.SellerProfile;
import com.auction.model.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class User extends Entity {

    private static final long serialVersionUID = 1L;

    /** QUY TAC DAT EMAIL */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9_-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");

    /** THUOC TINH */
    private String username;
    private String hashedPassword;
    private String email;
    private String fullName;
    private UserStatus userStatus;

    private final EnumMap<Role, RoleProfile> profiles = new EnumMap<>(Role.class);

    /** TAO USER MOI */
    public User (String username, String hashedPassword, String email, String fullName) {
        super();
        this.username       = validateUsername(username);
        this.hashedPassword = validatePassword(hashedPassword);
        this.email          = validateEmail(email);
        this.fullName       = validateFullName(fullName);
        this.userStatus     = UserStatus.ACTIVE;
    }

    /** LOAD USER TU DATBASE */
    public User(UUID id, LocalDateTime createdAt, LocalDateTime updatedAt,
                String username, String hashedPassword, String email,
                String fullName, UserStatus status) {
        super(id, createdAt, updatedAt);
        this.username       = validateUsername(username);
        this.hashedPassword = validatePassword(hashedPassword);
        this.email          = validateEmail(email);
        this.fullName       = validateFullName(fullName);
        this.userStatus     = Objects.requireNonNull(status, "status must not be null");
    }

    /** CAP ROLE CHO USER */
    public synchronized void grantRole(RoleProfile profile) {
        Objects.requireNonNull(profile, "profile khong the la null");
        Role role = Objects.requireNonNull(profile.getRole(), "profile.role khong the la null");
        if (profiles.containsKey(role)) {
            throw new IllegalStateException(
                    "User '" + username + "' đã có role " + role);
        }

        if (profile instanceof AdminProfile) {
            AdminProfile admin = (AdminProfile) profile;
            admin.attachOwner(this);
        }

        profiles.put(role, profile);
        markUpdated();
    }

    /** XOA ROLE */
    public synchronized boolean revokeRole(Role role) {
        Objects.requireNonNull(role);
        boolean removed = profiles.remove(role) != null;
        if (removed) markUpdated();
        return removed;
    }

    /** CHECK XEM CO ROLE KHONG */
    public boolean hasRole(Role role) {
        return profiles.containsKey(Objects.requireNonNull(role, "Role khong the la null"));
    }

    /** LAY DANH SACH ROLE */
    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(profiles.keySet());
    }

    /** */
    public Optional<BidderProfile> asBidder() {
        return getProfileAs(Role.BIDDER, BidderProfile.class);
    }

    public Optional<SellerProfile> asSeller() {
        return getProfileAs(Role.SELLER, SellerProfile.class);
    }

    public Optional<AdminProfile> asAdmin() {
        return getProfileAs(Role.ADMIN, AdminProfile.class);
    }

    /** */
    public BidderProfile requireBider() {
        return asBidder().orElseThrow(() ->
            new NoSuchElementException("User '" + username + "' không phải Bidder"));
    }

    /** */
    @SuppressWarnings("unchecked")
    public <P extends RoleProfile> Optional<P> getProfileAs(Role role, Class<P> type) {
        Objects.requireNonNull(role);
        Objects.requireNonNull(type);
        RoleProfile p = profiles.get(role);
        if (p == null) return Optional.empty();
        if (!type.isInstance(p)) {
            throw new ClassCastException(
                    "Profile của role " + role + " thực ra là " + p.getClass().getSimpleName() +
                            ", không phải " + type.getSimpleName());
        }
        return Optional.of((P) p);
    }

    /** */
    public boolean canBid() {
        return isActive() && hasRole(Role.BIDDER);
    }
    public boolean canSell()         {
        return isActive() && hasRole(Role.SELLER);
    }
    public boolean canManageSystem() {
        return isActive() && hasRole(Role.ADMIN);
    }

    /** */
    public boolean isActive() {
        return userStatus == UserStatus.ACTIVE;
    }
    public void active() {
        setUserStatus(UserStatus.ACTIVE);
    }
    public void ban() {
        setUserStatus(UserStatus.BANNED);
    }

    /** GETTER SETTER */
    public String getUsername() { return username; }
    public void setUsername(String username) {
        this.username = validateUsername(username);
        markUpdated();
    }

    public String getHashedPassword() { return hashedPassword; }
    public void setHashedPassword(String hashedPassword) {
        this.hashedPassword = validatePassword(hashedPassword);
        markUpdated();
    }

    public String getEmail() { return email; }
    public void setEmail(String email) {
        this.email = validateEmail(email);
        markUpdated();
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) {
        this.fullName = validateFullName(fullName);
        markUpdated();
    }

    public UserStatus getUserStatus() { return userStatus; }
    public void setUserStatus(UserStatus userStatus) {
        this.userStatus = Objects.requireNonNull(userStatus, "status must not be null");
        markUpdated();
    }

    /** VALIDATE */

    private static String validateUsername(String username) {
        Objects.requireNonNull(username, "username khong the la null");
        String trimmed = username.trim();
        if (trimmed.length() < 3 || trimmed.length() > 50) {
            throw new IllegalArgumentException("Username phai tu 3-50 ky tu");
        }
        return trimmed;
    }

    private static String validatePassword(String password) {
        Objects.requireNonNull(password, "password must not be null");
        if (password.isEmpty() || password.length() < 8) {
            throw new IllegalArgumentException("Password khong duoc ngan");
        }
        return password;
    }

    private static String validateEmail(String email) {
        Objects.requireNonNull(email, "Email khong duoc null");
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Email khong hop le");
        }
        return email;
    }

    private static String validateFullName(String fullName) {
        Objects.requireNonNull(fullName, "fullName must not be null");
        String trimmed = fullName.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Full name không được rỗng");
        }
        return trimmed;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + getId() +
                ", username='" + username + '\'' +
                ", roles=" + profiles.keySet() +
                ", status=" + userStatus +
                '}';
    }

    /** BUILDER PATTERN */
    public static final class Builder {
        private String username, hashedPassword, email, fullName;
        private final Map<Role, RoleProfile> stagedProfiles = new EnumMap<>(Role.class);

        public Builder username(String v)   { this.username = v; return this; }
        public Builder password(String v)   { this.hashedPassword = v; return this; }
        public Builder email(String v)      { this.email = v; return this; }
        public Builder fullName(String v)   { this.fullName = v; return this; }

        public Builder asBidder()                       { return withProfile(new BidderProfile()); }
        public Builder asBidder(java.math.BigDecimal initialBalance) {
            return withProfile(new BidderProfile(initialBalance));
        }
        public Builder asSeller()                       { return withProfile(new SellerProfile()); }
        public Builder asSeller(java.math.BigDecimal initialRevenue) {
            return withProfile(new SellerProfile(initialRevenue));
        }
        public Builder asAdmin()                        { return withProfile(new AdminProfile()); }

        public Builder withProfile(RoleProfile profile) {
            Objects.requireNonNull(profile);
            stagedProfiles.put(profile.getRole(), profile);
            return this;
        }

        public User build() {
            User u = new User(username, hashedPassword, email, fullName);
            stagedProfiles.values().forEach(u::grantRole);
            return u;
        }
    }
}

