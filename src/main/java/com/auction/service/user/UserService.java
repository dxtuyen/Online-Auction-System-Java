package com.auction.service.user;

import com.auction.dto.user.UserDto;
import com.auction.dto.user.UserLoginRequestDto;
import com.auction.dto.user.UserRegisterRequestDto;
import com.auction.model.entity.User;
import com.auction.repository.user.UserRepository;
import com.auction.repository.user.UserRepositoryImpl;

import java.util.Optional;

public class UserService {
    UserRepository userRepository = new UserRepositoryImpl();
    public UserDto register(UserRegisterRequestDto userRegisterRequest) {
        String username = userRegisterRequest.username();
        String password = userRegisterRequest.password();

        if (username == null || username.isBlank()) {
            throw new RuntimeException("Username khong duoc de trong");
        }
        if (password == null || password.isBlank()) {
            throw new RuntimeException("Password khong duoc de trong");
        }

        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username da ton tai");
        }

        User user = userRepository.create(username, password);
        return new UserDto(user.getUsername());
    }

    public boolean login(UserLoginRequestDto userLoginRequest) {
        String username = userLoginRequest.username();
        Optional<User> result = userRepository.findByUsername(username);
        String password = userLoginRequest.password();

        if (result.isPresent() && result.get().getPassword().equals(password)) {
            return true;
        } else {
            return false;
        }
    }
}
