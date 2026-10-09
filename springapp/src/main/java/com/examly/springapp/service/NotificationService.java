package com.examly.springapp.service;

import com.examly.springapp.dto.NotificationDTO;
import com.examly.springapp.dto.NotificationListDTO;
import com.examly.springapp.model.User;

import java.util.List;

/**
 * In-app notification workflow shared by the inquiry and feedback flows.
 *
 * <p>All read methods are scoped to a single recipient id that is always derived from the
 * authenticated principal on the server — never from a request parameter — so one user can
 * neither read nor modify another user's notifications.</p>
 */
public interface NotificationService {

    /**
     * Persists one notification for one recipient. When {@code eventKey} is supplied the write is
     * idempotent: repeating the same business event for the same recipient does not create a
     * second notification (and a unique-constraint race is handled as a no-op).
     */
    void notifyUser(User recipient, String type, String title, String message,
                    Long relatedInquiryId, Long relatedFeedbackId, String eventKey);

    /**
     * Persists one notification per intended administrator (Admin and SuperAdmin). Each
     * recipient receives their own row so unread counts and read states stay independent.
     */
    void notifyAdministrators(String type, String title, String message,
                              Long relatedInquiryId, Long relatedFeedbackId, String eventKey);

    NotificationListDTO getMyNotifications(Long userId, int page, int size);

    long getUnreadCount(Long userId);

    NotificationDTO markAsRead(Long userId, Long notificationId);

    /** @return how many notifications were updated */
    long markAllAsRead(Long userId);

    /** Helper used by the workflow services to build stable, collision-free idempotency keys. */
    static String eventKey(String prefix, Object... parts) {
        StringBuilder builder = new StringBuilder(prefix);
        for (Object part : parts) {
            builder.append(':').append(part == null ? "" : part);
        }
        String key = builder.toString();
        return key.length() <= 120 ? key : key.substring(0, 120);
    }
}
