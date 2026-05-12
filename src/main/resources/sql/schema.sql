CREATE DATABASE IF NOT EXISTS auction_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE auction_db;

CREATE TABLE IF NOT EXISTS users (
    id            CHAR(36)       NOT NULL PRIMARY KEY,
    created_at    DATETIME(3)    NOT NULL,
    updated_at    DATETIME(3)    NOT NULL,
    username      VARCHAR(50)    NOT NULL UNIQUE,
    hashed_password VARCHAR(255) NOT NULL,
    password_salt VARCHAR(255)   NOT NULL,
    email         VARCHAR(254)   NOT NULL UNIQUE,
    full_name     VARCHAR(100)   NOT NULL,
    user_status   ENUM('ACTIVE','BANNED') NOT NULL DEFAULT 'ACTIVE',
    role          ENUM('ADMIN','NORMAL')  NOT NULL DEFAULT 'NORMAL',
    balance       DECIMAL(19,4)  NOT NULL DEFAULT 0,
    revenue       DECIMAL(19,4)  NOT NULL DEFAULT 0
);

-- Single-table inheritance: một bảng cho mọi loại Item
CREATE TABLE IF NOT EXISTS items (
    id              CHAR(36)       NOT NULL PRIMARY KEY,
    created_at      DATETIME(3)    NOT NULL,
    updated_at      DATETIME(3)    NOT NULL,
    item_type       ENUM('ELECTRONICS','ART','VEHICLE','OTHER') NOT NULL,
    name            VARCHAR(200)   NOT NULL,
    description     TEXT,
    seller_id       CHAR(36)       NOT NULL,
    starting_price  DECIMAL(19,4)  NOT NULL,
    category        VARCHAR(50)    NOT NULL,
    item_condition  VARCHAR(50)    NOT NULL,

    -- Electronics
    brand           VARCHAR(100),
    model           VARCHAR(100),
    warranty_months INT,

    -- Art
    artist          VARCHAR(100),
    year_created    INT,
    medium          VARCHAR(100),

    -- Vehicle
    make            VARCHAR(100),
    vehicle_model   VARCHAR(100),
    vehicle_year    INT,
    mileage_km      INT,

    -- OtherItem (FASHION/COLLECTIBLE/OTHER)
    extra_info      TEXT,

    CONSTRAINT fk_items_seller FOREIGN KEY (seller_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS item_images (
    item_id   CHAR(36)     NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    position  INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (item_id, position),
    CONSTRAINT fk_images_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS auctions (
    id                  CHAR(36)      NOT NULL PRIMARY KEY,
    created_at          DATETIME(3)   NOT NULL,
    updated_at          DATETIME(3)   NOT NULL,
    item_id             CHAR(36)      NOT NULL UNIQUE,
    seller_id           CHAR(36)      NOT NULL,
    start_time          DATETIME(3)   NOT NULL,
    end_time            DATETIME(3)   NOT NULL,
    starting_price      DECIMAL(19,4) NOT NULL,
    current_price       DECIMAL(19,4) NOT NULL,
    minimum_increment   DECIMAL(19,4) NOT NULL,
    highest_bidder_id   CHAR(36),
    status              ENUM('PENDING','RUNNING','FINISHED','PAID','CANCELED') NOT NULL DEFAULT 'PENDING',
    total_bids          INT           NOT NULL DEFAULT 0,

    CONSTRAINT fk_auction_item     FOREIGN KEY (item_id)           REFERENCES items(id),
    CONSTRAINT fk_auction_seller   FOREIGN KEY (seller_id)         REFERENCES users(id),
    CONSTRAINT fk_auction_winner   FOREIGN KEY (highest_bidder_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS bid_transactions (
    id          CHAR(36)      NOT NULL PRIMARY KEY,
    created_at  DATETIME(3)   NOT NULL,
    updated_at  DATETIME(3)   NOT NULL,
    auction_id  CHAR(36)      NOT NULL,
    bidder_id   CHAR(36)      NOT NULL,
    bid_amount  DECIMAL(19,4) NOT NULL,
    status      ENUM('PENDING','VALID','OUTBID','REJECTED','CANCELED') NOT NULL DEFAULT 'PENDING',

    CONSTRAINT fk_bid_auction FOREIGN KEY (auction_id) REFERENCES auctions(id),
    CONSTRAINT fk_bid_bidder  FOREIGN KEY (bidder_id)  REFERENCES users(id)
);

CREATE INDEX idx_bids_auction ON bid_transactions(auction_id);

CREATE TABLE IF NOT EXISTS auto_bids (
    bidder_id   CHAR(36)      NOT NULL,
    auction_id  CHAR(36)      NOT NULL,
    max_bid     DECIMAL(19,4) NOT NULL,
    increment   DECIMAL(19,4) NOT NULL,
    created_at  DATETIME(3)   NOT NULL,
    active      TINYINT(1)    NOT NULL DEFAULT 1,
    PRIMARY KEY (bidder_id, auction_id),

    CONSTRAINT fk_autobid_bidder  FOREIGN KEY (bidder_id)  REFERENCES users(id),
    CONSTRAINT fk_autobid_auction FOREIGN KEY (auction_id) REFERENCES auctions(id)
);
