package com.examly.springapp.service;

import com.examly.springapp.exceptions.InvestmentException;


import com.examly.springapp.model.Feedback;
import com.examly.springapp.model.Investment;
import com.examly.springapp.repository.FeedbackRepo;
import com.examly.springapp.repository.InvestmentRepo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class FeedbackServiceImpl implements FeedbackService {
    
    private final FeedbackRepo feedbackRepository;
    private final InvestmentRepo investmentRepository;
    
    public FeedbackServiceImpl(
            FeedbackRepo feedbackRepository,
            InvestmentRepo investmentRepository) {
        this.feedbackRepository = feedbackRepository;
        this.investmentRepository = investmentRepository;
    }

    @Override
    @Transactional
    public Feedback createFeedback(Feedback feedback) {
        String category = feedback.getCategory() == null ? "" : feedback.getCategory().trim();
        String feedbackText = feedback.getFeedbackText() == null ? "" : feedback.getFeedbackText().trim();
        if (category.isEmpty() || feedbackText.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feedback type and feedback text are required.");
        }
        feedback.setCategory(category);
        feedback.setFeedbackText(feedbackText);

        if ("Platform".equals(category)) {
            if (feedback.getInvestment() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Platform feedback cannot be associated with an investment.");
            }
            feedback.setInvestment(null);
            feedback.setSubject(null);
            feedback.setInvestmentFeedbackUserId(null);
        } else if ("Other".equals(category)) {
            if (feedback.getInvestment() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Other feedback cannot be associated with an investment.");
            }
            String subject = feedback.getSubject() == null ? "" : feedback.getSubject().trim();
            if (subject.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A subject is required for Other feedback.");
            }
            feedback.setSubject(subject);
            feedback.setInvestment(null);
            feedback.setInvestmentFeedbackUserId(null);
        } else {
            if (feedback.getInvestment() == null || feedback.getInvestment().getInvestmentId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an investment for investment feedback.");
            }
            if (feedback.getUser() == null || feedback.getUser().getUserId() == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "An authenticated user is required.");
            }

            Long investmentId = feedback.getInvestment().getInvestmentId();
            Investment investment = investmentRepository.findById(investmentId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Investment not found."));
            feedback.setInvestment(investment);

            boolean alreadySubmitted = feedbackRepository
                    .findByUser_UserIdAndInvestment_InvestmentId(feedback.getUser().getUserId(), investmentId)
                    .stream()
                    .anyMatch(existing -> !"Platform".equals(existing.getCategory())
                            && !"Other".equals(existing.getCategory()));
            if (alreadySubmitted) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "You have already submitted feedback for this investment.");
            }
            feedback.setSubject(null);
            feedback.setInvestmentFeedbackUserId(feedback.getUser().getUserId());
        }

        return feedbackRepository.saveAndFlush(feedback);
    }

    @Override
    public Feedback getFeedbackById(Long feedbackId) {
        return feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new InvestmentException("Feedback not found with id: " + feedbackId));
    }

    @Override
    public List<Feedback> getAllFeedbacks() {
        return feedbackRepository.findAll();
    }

    @Override
    public Feedback deleteFeedback(Long feedbackId) {
        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new InvestmentException("Feedback not found with id: " + feedbackId));
        feedbackRepository.delete(feedback);
        return feedback;
    }

    @Override
    public List<Feedback> getFeedbacksByUserId(Long userId) {
        return feedbackRepository.findByUser_UserId(userId);
    }

    @Override
    public List<Feedback> getFeedbacksByInvestmentId(Long investmentId) {
        return feedbackRepository.findByInvestment_InvestmentId(investmentId);
    }
}