package com.examly.springapp.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Entity
@Table(name = "investment_inquiries")
public class InvestmentInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long inquiryId;

    @ManyToOne
    @JoinColumn(name = "userId", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "investmentId", nullable = false)
    private Investment investment;

    @Column(nullable = false, columnDefinition = "TEXT")
    @NotBlank
    @Size(max = 4000)
    private String message;

    @Column
    private String status;

    @Column(length = 255)
    private String subject;

    @Column(nullable = false)
    @NotBlank
    private String priority;

    @Column
    private LocalDateTime inquiryDate;

    @Column
    private LocalDateTime responseDate;

    @Column(columnDefinition = "TEXT")
    private String adminResponse;

    @Column
    private String contactDetails;

    public InvestmentInquiry() {}

    public Long getInquiryId() { return inquiryId; }
    public void setInquiryId(Long inquiryId) { this.inquiryId = inquiryId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Investment getInvestment() { return investment; }
    public void setInvestment(Investment investment) { this.investment = investment; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public LocalDateTime getInquiryDate() { return inquiryDate; }
    public void setInquiryDate(LocalDateTime inquiryDate) { this.inquiryDate = inquiryDate; }

    public LocalDateTime getResponseDate() { return responseDate; }
    public void setResponseDate(LocalDateTime responseDate) { this.responseDate = responseDate; }

    public String getAdminResponse() { return adminResponse; }
    public void setAdminResponse(String adminResponse) { this.adminResponse = adminResponse; }

    public String getContactDetails() { return contactDetails; }
    public void setContactDetails(String contactDetails) { this.contactDetails = contactDetails; }
}