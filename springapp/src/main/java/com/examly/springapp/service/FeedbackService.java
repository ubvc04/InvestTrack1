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
}