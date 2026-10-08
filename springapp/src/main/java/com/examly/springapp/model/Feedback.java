package com.examly.springapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "feedbacks", uniqueConstraints = @UniqueConstraint(
        name = "uk_feedback_investment_user_once",
        columnNames = {"investmentId", "investmentFeedbackUserId"}))
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long feedbackId;

    @Column(nullable = false, columnDefinition = "TEXT")
    @NotBlank
    @Size(max = 4000)
    private String feedbackText;

    @Column(nullable = false)
    @NotBlank
    private String date;

    @ManyToOne
    @JoinColumn(name = "userId", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "investmentId", nullable = true)
    private Investment investment;

    @Column(nullable = false)
    @NotBlank
    private String category;

    @Column(length = 255)
    @Size(max = 255)
    private String subject;

    @JsonIgnore
    @Column
    private Long investmentFeedbackUserId;

    public Feedback() {}

    public Long getFeedbackId() { return feedbackId; }
    public void setFeedbackId(Long feedbackId) { this.feedbackId = feedbackId; }

    public String getFeedbackText() { return feedbackText; }
    public void setFeedbackText(String feedbackText) { this.feedbackText = feedbackText; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Investment getInvestment() { return investment; }
    public void setInvestment(Investment investment) { this.investment = investment; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public Long getInvestmentFeedbackUserId() { return investmentFeedbackUserId; }
    public void setInvestmentFeedbackUserId(Long investmentFeedbackUserId) {
        this.investmentFeedbackUserId = investmentFeedbackUserId;
    }
}