package com.auction.server;

import java.util.UUID;

public final class Session {

    private volatile UUID currentUserId;

    public UUID getCurrentUserId() {
        return currentUserId;
    }

    public void setCurrentUserId(UUID userId) {
        this.currentUserId = userId;
    }

    public boolean isAuthenticated() {
        return currentUserId != null;
    }

    public void clear() {
        this.currentUserId = null;
    }
}
