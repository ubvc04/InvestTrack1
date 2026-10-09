package com.examly.springapp.controller;

import com.examly.springapp.dto.NotificationDTO;
import com.examly.springapp.dto.NotificationListDTO;
import com.examly.springapp.dto.NotificationUnreadCountDTO;
import com.examly.springapp.model.User;
import com.examly.springapp.repository.UserRepo;
import com.examly.springapp.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Notification endpoints for USER, ADMIN and SUPER_ADMIN.
 *
 * <p>The recipient is always resolved from the authenticated principal (the JWT subject, i.e.
 * the e-mail address) — the client never supplies a recipient id, so a user cannot list, read
 * or mark another user's notifications by changing a parameter.</p>
 */
@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "http://localhost:8081")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepo userRepository;

    public NotificationController(NotificationService notificationService, UserRepo userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    /** Paged notification history of the authenticated user, newest first. */
    @GetMapping
    public ResponseEntity<NotificationListDTO> getMyNotifications(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User current = currentUser(authentication);
        return ResponseEntity.ok(notificationService.getMyNotifications(
                current.getUserId(), page, size));
    }

    /** Unread badge count, straight from the database. */
    @GetMapping("/unread-count")
    public ResponseEntity<NotificationUnreadCountDTO> unreadCount(Authentication authentication) {
        User current = currentUser(authentication);
        return ResponseEntity.ok(new NotificationUnreadCountDTO(
                notificationService.getUnreadCount(current.getUserId())));
    }

    /** Marks one of the caller's notifications as read; 404 for any other user's id. */
    @PutMapping("/{notificationId}/read")
    public ResponseEntity<NotificationDTO> markAsRead(Authentication authentication,
                                                      @PathVariable Long notificationId) {
        User current = currentUser(authentication);
        return ResponseEntity.ok(notificationService.markAsRead(
                current.getUserId(), notificationId));
    }

    /** Marks every unread notification of the caller as read. */
    @PutMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead(Authentication authentication) {
        User current = currentUser(authentication);
        long updated = notificationService.markAllAsRead(current.getUserId());
        return ResponseEntity.ok(Map.<String, Object>of(
                "message", "Notifications marked as read",
                "updated", updated,
                "unreadCount", notificationService.getUnreadCount(current.getUserId())));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user no longer exists"));
    }
}
