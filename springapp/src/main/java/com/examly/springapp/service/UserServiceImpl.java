package com.examly.springapp.service;

import com.examly.springapp.exceptions.InvestmentException;
import com.examly.springapp.model.User;
import com.examly.springapp.repository.UserRepo;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepo userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    
    public UserServiceImpl(
            UserRepo userRepository,
            BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public User createUser(User user) {
        user.setEmail(normalizeEmail(user.getEmail()));
        user.setUserRole("User");
        user.setMustChangePassword(false);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User createAdmin(User user) {
        user.setEmail(normalizeEmail(user.getEmail()));
        user.setUserRole("Admin");
        user.setMustChangePassword(false);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    @Override
    public User loginUser(User user) {
        String email = normalizeEmail(user.getEmail());
        User existingUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvestmentException("User not found with email: " + user.getEmail()));

        if (!passwordEncoder.matches(user.getPassword(), existingUser.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }
        return existingUser;
    }

    @Override
public void changePassword(String email,
                           String oldPassword,
                           String newPassword) {

    User user = userRepository.findByEmail(normalizeEmail(email))
            .orElseThrow(() ->
                    new InvestmentException("User not found"));

    if (!passwordEncoder.matches(
            oldPassword,
            user.getPassword())) {

        throw new RuntimeException(
                "Old password is incorrect");
    }

    user.setPassword(
            passwordEncoder.encode(newPassword));
    user.setMustChangePassword(false);

    userRepository.save(user);
}

    @Override
    public List<User> getAdmins() {
        return userRepository.findByUserRoleIgnoreCase("Admin");
    }

    @Override
    public boolean mustChangePassword(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
                .map(User::isMustChangePassword)
                .orElse(true);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}