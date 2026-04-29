package com.auction.server.dao;

import com.auction.model.entity.User;
import java.util.List;

public interface UserDAO {
  // --- Nhóm các hàm CRUD cơ bản ---

  /**
   * Thêm một user mới vào hệ thống.
   */
  int addUser(User user);

  /**
   * Lấy thông tin user theo ID.
   */
  User getUserById(int id);

  /**
   * Cập nhật thông tin user.
   */
  boolean updateUser(User user);

  /**
   * Xóa user(Admin).
   */
  boolean deleteUser(int id); //Xóa user(Admin)

  // --- Nhóm các hàm nghiệp vụ ---

  /**
   * Tìm kiếm user theo username.
   */
  User getUserByUsername(String username);

  /**
   * Lấy danh sách user.
   */
  List<User> getAllUsers();

  /**
   * Kiểm tra username có tồn tại không.
   */
  boolean isUsernameExists(String username);

  /**
   * Kiểm tra xem email có tồn tại chưa.
   */
  boolean isEmailExists(String email);
}
