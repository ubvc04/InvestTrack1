package com.examly.springapp.service;

import com.examly.springapp.model.Feedback;
import java.util.List;

public interface FeedbackService {
    Feedback createFeedback(Feedback feedback);
    Feedback getFeedbackById(Long feedbackId);
    List<Feedback> getAllFeedbacks();
    Feedback deleteFeedback(Long feedbackId);
    List<Feedback> getFeedbacksByUserId(Long userId);
    List<Feedback> getFeedbacksByInvestmentId(Long investmentId);

    /**
     * Stores an administrative response on a feedback entry and notifies the user who
     * submitted it. Idempotent for an identical repeated response.
     */
    Feedback respondToFeedback(Long feedbackId, String adminResponse);
}
