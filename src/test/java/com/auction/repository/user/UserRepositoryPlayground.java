package com.auction.repository.user;

import com.auction.model.entity.User;

import java.util.Optional;
import java.util.UUID;

public class UserRepositoryPlayground {

    private static final UserRepository repo = new UserRepositoryImpl();
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== UserRepository Playground ===\n");

        testCreate();
        testFindByIdFound();
        testFindByIdNotFound();
        testFindByUsernameFound();
        testFindByUsernameNotFound();

        System.out.printf("%n--- Results: %d passed, %d failed ---%n", passed, failed);
    }

    // ─── create ──────────────────────────────────────────────────────────────

    static void testCreate() {
        String label = "create: returns user with correct name and non-null id";
        try {
            User user = repo.create("testuser_" + System.currentTimeMillis(), "pass123");
            if (user != null && user.getName() != null && user.getId() != null) {
                pass(label);
                System.out.println("       created id=" + user.getId() + " name=" + user.getName());
            } else {
                fail(label, "returned null fields");
            }
        } catch (Exception e) {
            fail(label, e.getMessage());
        }
    }

    // ─── findById ────────────────────────────────────────────────────────────

    static void testFindByIdFound() {
        String label = "findById: returns user when id exists";
        try {
            // create a user first, then look it up by id
            User created = repo.create("findbyid_" + System.currentTimeMillis(), "pw");
            Optional<User> found = repo.findById(created.getId());
            if (found.isPresent() && found.get().getId().equals(created.getId())) {
                pass(label);
            } else {
                fail(label, "user not found or id mismatch");
            }
        } catch (Exception e) {
            fail(label, e.getMessage());
        }
    }

    static void testFindByIdNotFound() {
        String label = "findById: returns empty for unknown id";
        try {
            Optional<User> result = repo.findById(UUID.randomUUID());
            if (result.isEmpty()) {
                pass(label);
            } else {
                fail(label, "expected empty but got " + result.get().getId());
            }
        } catch (Exception e) {
            fail(label, e.getMessage());
        }
    }

    // ─── findByUsername ───────────────────────────────────────────────────────

    static void testFindByUsernameFound() {
        String label = "findByUsername: returns user when username exists";
        try {
            String username = "findbyname_" + System.currentTimeMillis();
            repo.create(username, "pw");
            Optional<User> found = repo.findByUsername(username);
            if (found.isPresent() && username.equals(found.get().getName())) {
                pass(label);
            } else {
                fail(label, "user not found or name mismatch");
            }
        } catch (Exception e) {
            fail(label, e.getMessage());
        }
    }

    static void testFindByUsernameNotFound() {
        String label = "findByUsername: returns empty for unknown username";
        try {
            Optional<User> result = repo.findByUsername("__definitely_not_exist__");
            if (result.isEmpty()) {
                pass(label);
            } else {
                fail(label, "expected empty but got " + result.get().getName());
            }
        } catch (Exception e) {
            fail(label, e.getMessage());
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    static void pass(String label) {
        passed++;
        System.out.println("[PASS] " + label);
    }

    static void fail(String label, String reason) {
        failed++;
        System.out.println("[FAIL] " + label);
        System.out.println("       reason: " + reason);
    }
}
