package com.examly.springapp.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Locale;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false)
    @NotBlank
    @Size(max = 80)
    private String username;

    @Column(nullable = false, unique = true)
    @NotBlank
    @Email
    private String email;

    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    @Column(nullable = false)
    @NotBlank
    @Pattern(regexp = "^[0-9+() -]{7,20}$")
    private String mobileNumber;

    @Column(nullable = false)
    private String userRole;

    @Column(nullable = false)
    private boolean mustChangePassword = false;

    public User() {}

    public User(Long userId, String username, String email, String password, String mobileNumber, String userRole) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.password = password;
        this.mobileNumber = mobileNumber;
        this.userRole = userRole;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

    public String getUserRole() { return userRole; }
    public void setUserRole(String userRole) { this.userRole = userRole; }

    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }

}
