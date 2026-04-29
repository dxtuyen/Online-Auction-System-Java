package com.auction.server.dao;

import com.auction.model.entity.User;
import java.util.List;

public interface UserDAO {
  //Nhóm các hàm CRUD
  int addUser(User user); //Thêm một user mới

  User getUserById(int id); //Lấy thông tin user theo id

  boolean updateUser(User user); //Cập nhật thông tin user

  boolean deleteUser(int id); //Xóa user(Admin)

  //Nhóm các hàm nghiệp vụ
  User getUserByUsername(String username); //Tìm kiếm user

  List<User> getAllUsers(); //Lấy danh sách các user(Admin)

  boolean isUsernameExists(String username); //Kiểm tra xem username tồn tại không

  boolean isEmailExists(String email); //Kiểm tra xem email này có đã tồn tại chưa
}
