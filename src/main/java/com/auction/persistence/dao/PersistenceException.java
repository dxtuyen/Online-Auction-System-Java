package com.auction.persistence.dao;

/**
 * Unchecked exception wrap mọi SQLException từ DAO.
 * Service layer không cần catch checked SQLException khắp nơi.
 */
public class PersistenceException extends RuntimeException {

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
