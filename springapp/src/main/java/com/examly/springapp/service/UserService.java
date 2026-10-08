package com.examly.springapp.service;

import com.examly.springapp.model.User;

public interface UserService {
    User createUser(User user);
    User loginUser(User user);
    User createAdmin(User user);
    void changePassword(String email,
        String oldPassword,
        String newPassword);
    java.util.List<User> getAdmins();
    boolean mustChangePassword(String email);
}