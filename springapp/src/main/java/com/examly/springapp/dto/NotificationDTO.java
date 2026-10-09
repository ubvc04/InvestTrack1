package com.examly.springapp.dto;

import java.time.LocalDateTime;

/**
 * Notification summary returned to the client.
 *
 * @param read               read state of the current recipient only
 * @param relatedInquiryId   optional link to the inquiry that caused the notification
 * @param relatedFeedbackId  optional link to the feedback that caused the notification
 */
public record NotificationDTO(
        Long notificationId,
        String type,
        String title,
        String message,
        boolean read,
        LocalDateTime createdAt,
        Long relatedInquiryId,
        Long relatedFeedbackId) {
}
