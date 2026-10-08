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
@Order(2)
public class SuperAdminInitializer implements CommandLineRunner {

    private static final String SUPER_ADMIN_ROLE = "SuperAdmin";

    private final UserRepo userRepo;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${investtrack.default-superadmin.username:Mukund}")
    private String username;

    @Value("${investtrack.default-superadmin.email:mukundmaheshwari1568@gmail.com}")
    private String email;

    @Value("${investtrack.default-superadmin.password:Mukund@123}")
    private String password;

    @Value("${investtrack.default-superadmin.phone:1234567890}")
    private String phone;

    public SuperAdminInitializer(UserRepo userRepo, BCryptPasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepo.existsByUserRoleIgnoreCase(SUPER_ADMIN_ROLE)) {
            return;
        }

        User superAdmin = new User();
        superAdmin.setUsername(username);
        superAdmin.setEmail(email);
        superAdmin.setPassword(passwordEncoder.encode(password));
        superAdmin.setMobileNumber(phone);
        superAdmin.setUserRole(SUPER_ADMIN_ROLE);
        superAdmin.setMustChangePassword(true);
        userRepo.save(superAdmin);
    }
}