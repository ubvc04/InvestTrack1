package com.examly.springapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * An in-app notification belonging to exactly one recipient.
 *
 * <p>One row per recipient: if several administrators are meant to see the same business event
 * each of them gets their own row, so every recipient keeps an independent read state and an
 * independent unread count.</p>
 *
 * <p>{@code eventKey} is the idempotency key of the business event (unique per recipient through
 * {@code uk_notification_recipient_event}), which makes repeated API submissions, duplicate event
 * handlers, page refreshes and repeated application startups unable to create a second copy of
 * the same notification.</p>
 */
@Entity
@Table(name = "notifications", uniqueConstraints = @UniqueConstraint(
        name = "uk_notification_recipient_event",
        columnNames = {"userId", "eventKey"}))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    private User user;

    /** INQUIRY | FEEDBACK | GENERAL */
    @NotBlank
    @Size(max = 40)
    @Column(nullable = false, length = 40)
    private String type;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String title;

    @NotBlank
    @Size(max = 1000)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** Optional link to the related business record. */
    private Long relatedInquiryId;

    private Long relatedFeedbackId;

    /** Idempotency key of the event, unique per recipient. */
    @Size(max = 120)
    @Column(length = 120)
    private String eventKey;

    public Notification() {
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(Long notificationId) {
        this.notificationId = notificationId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getRelatedInquiryId() {
        return relatedInquiryId;
    }

    public void setRelatedInquiryId(Long relatedInquiryId) {
        this.relatedInquiryId = relatedInquiryId;
    }

    public Long getRelatedFeedbackId() {
        return relatedFeedbackId;
    }

    public void setRelatedFeedbackId(Long relatedFeedbackId) {
        this.relatedFeedbackId = relatedFeedbackId;
    }

    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String eventKey) {
        this.eventKey = eventKey;
    }
}
