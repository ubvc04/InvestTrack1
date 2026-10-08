package com.examly.springapp.repository;

import com.examly.springapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.List;

@Repository
public interface UserRepo extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByUserRoleIgnoreCase(String userRole);
    boolean existsByUserRoleIgnoreCase(String userRole);

    @Query("select u from User u where lower(u.userRole) = lower(:role) order by u.userId")
    List<User> findAdmins(String role);
}