package com.examly.springapp.service;

import com.examly.springapp.dto.NotificationDTO;
import com.examly.springapp.dto.NotificationListDTO;
import com.examly.springapp.model.Notification;
import com.examly.springapp.model.User;
import com.examly.springapp.repository.NotificationRepo;
import com.examly.springapp.repository.UserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private static final String ROLE_ADMIN = "Admin";
    private static final String ROLE_SUPER_ADMIN = "SuperAdmin";

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final NotificationRepo notificationRepository;
    private final UserRepo userRepository;

    public NotificationServiceImpl(NotificationRepo notificationRepository, UserRepo userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void notifyUser(User recipient, String type, String title, String message,
                           Long relatedInquiryId, Long relatedFeedbackId, String eventKey) {
        if (recipient == null || recipient.getUserId() == null) {
            logger.warn("Notification skipped: no valid recipient (title={})", title);
            return;
        }
        if (eventKey != null
                && notificationRepository.existsByUser_UserIdAndEventKey(recipient.getUserId(), eventKey)) {
            logger.debug("Notification skipped: event {} already delivered to user {}",
                    eventKey, recipient.getUserId());
            return;
        }

        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setType(truncate(type, 40));
        notification.setTitle(truncate(title, 200));
        notification.setMessage(truncate(message, 1000));
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRelatedInquiryId(relatedInquiryId);
        notification.setRelatedFeedbackId(relatedFeedbackId);
        notification.setEventKey(truncate(eventKey, 120));

        try {
            notificationRepository.save(notification);
            logger.info("Notification created: type={} recipient={} event={}",
                    type, recipient.getUserId(), eventKey);
        } catch (DataIntegrityViolationException exception) {
            // Another thread or a repeated submission inserted the same event first.
            logger.info("Notification duplicate ignored: type={} recipient={} event={}",
                    type, recipient.getUserId(), eventKey);
        }
    }

    @Override
    @Transactional
    public void notifyAdministrators(String type, String title, String message,
                                     Long relatedInquiryId, Long relatedFeedbackId, String eventKey) {
        Set<User> administrators = new LinkedHashSet<>();
        administrators.addAll(userRepository.findByUserRoleIgnoreCase(ROLE_ADMIN));
        administrators.addAll(userRepository.findByUserRoleIgnoreCase(ROLE_SUPER_ADMIN));

        if (administrators.isEmpty()) {
            logger.info("No administrators available for notification event {}", eventKey);
            return;
        }
        for (User administrator : administrators) {
            notifyUser(administrator, type, title, message, relatedInquiryId, relatedFeedbackId, eventKey);
        }
    }

    @Override
    public NotificationListDTO getMyNotifications(Long userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<Notification> result = notificationRepository
                .findByUser_UserIdOrderByCreatedAtDesc(userId, pageable);
        List<NotificationDTO> items = result.getContent().stream()
                .map(NotificationServiceImpl::toDto)
                .toList();
        return new NotificationListDTO(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(),
                notificationRepository.countByUser_UserIdAndReadFalse(userId));
    }

    @Override
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUser_UserIdAndReadFalse(userId);
    }

    @Override
    @Transactional
    public NotificationDTO markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository
                .findByUser_UserIdAndNotificationId(userId, notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Notification not found"));
        if (!notification.isRead()) {
            notification.setRead(true);
            notificationRepository.save(notification);
        }
        return toDto(notification);
    }

    @Override
    @Transactional
    public long markAllAsRead(Long userId) {
        List<Notification> unread = notificationRepository.findByUser_UserIdAndReadFalse(userId);
        if (unread.isEmpty()) {
            return 0;
        }
        unread.forEach(notification -> notification.setRead(true));
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    private static NotificationDTO toDto(Notification notification) {
        return new NotificationDTO(
                notification.getNotificationId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getRelatedInquiryId(),
                notification.getRelatedFeedbackId());
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
