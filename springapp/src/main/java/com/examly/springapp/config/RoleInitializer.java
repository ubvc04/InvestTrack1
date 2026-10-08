package com.examly.springapp.config;

import com.examly.springapp.model.User;
import com.examly.springapp.repository.UserRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Component
@Order(1)
public class RoleInitializer implements CommandLineRunner {
    private final UserRepo userRepo;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${investtrack.superadmin.email:baveshchowdary2022@gmail.com}")
    private String superAdminEmail;

    @Value("${investtrack.superadmin.username:SuperAdmin}")
    private String superAdminUsername;

    @Value("${investtrack.superadmin.password:}")
    private String superAdminPassword;

    public RoleInitializer(UserRepo userRepo, BCryptPasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        User superAdmin = userRepo.findByEmail(superAdminEmail.trim().toLowerCase())
                .orElseGet(() -> createConfiguredSuperAdminIfConfigured());
        if (superAdmin == null) {
            return;
        }

        boolean alreadySuperAdmin = "SuperAdmin".equalsIgnoreCase(superAdmin.getUserRole());
        superAdmin.setUserRole("SuperAdmin");
        if (!alreadySuperAdmin) {
            superAdmin.setMustChangePassword(true);
        }
        userRepo.save(superAdmin);

        userRepo.findByUserRoleIgnoreCase("SuperAdmin").stream()
                .filter(user -> !user.getUserId().equals(superAdmin.getUserId()))
                .forEach(user -> {
                    user.setUserRole("User");
                    user.setMustChangePassword(false);
                    userRepo.save(user);
                });
    }

    private User createConfiguredSuperAdminIfConfigured() {
        if (superAdminPassword == null || superAdminPassword.isBlank()) {
            return null;
        }
        User user = new User();
        user.setUsername(superAdminUsername);
        user.setEmail(superAdminEmail);
        user.setPassword(passwordEncoder.encode(superAdminPassword));
        user.setMobileNumber("0000000000");
        user.setUserRole("SuperAdmin");
        user.setMustChangePassword(true);
        return user;
    }

}