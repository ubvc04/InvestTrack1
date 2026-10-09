package com.examly.springapp.dto;

import java.util.List;

/**
 * Paged notification list plus the recipient's unread count, so the bell badge and the list
 * always come from the same database read.
 */
public record NotificationListDTO(
        List<NotificationDTO> items,
        int page,
        int size,
        long totalElements,
        long totalPages,
        long unreadCount) {
}
