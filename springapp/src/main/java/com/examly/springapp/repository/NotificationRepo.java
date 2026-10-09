package com.examly.springapp.repository;

import com.examly.springapp.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepo extends JpaRepository<Notification, Long> {

    List<Notification> findByUser_UserIdOrderByCreatedAtDesc(Long userId);

    Page<Notification> findByUser_UserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Recipient-scoped lookup: a notification can only ever be resolved for its own owner. */
    Optional<Notification> findByUser_UserIdAndNotificationId(Long userId, Long notificationId);

    /** Idempotency check for the unique (userId, eventKey) constraint. */
    boolean existsByUser_UserIdAndEventKey(Long userId, String eventKey);

    long countByUser_UserIdAndReadFalse(Long userId);

    List<Notification> findByUser_UserIdAndReadFalse(Long userId);
}
