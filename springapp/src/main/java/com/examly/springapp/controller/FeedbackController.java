package com.examly.springapp.controller;

import com.examly.springapp.dto.ApiDtoMapper;
import com.examly.springapp.dto.FeedbackDTO;
import com.examly.springapp.dto.RespondToFeedbackDTO;
import com.examly.springapp.model.Feedback;
import com.examly.springapp.model.User;
import com.examly.springapp.repository.UserRepo;
import com.examly.springapp.service.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
@CrossOrigin(origins = "http://localhost:8081")
public class FeedbackController {
    private final FeedbackService feedbackService;
    private final UserRepo userRepository;

    public FeedbackController(FeedbackService feedbackService, UserRepo userRepository) {
        this.feedbackService = feedbackService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<FeedbackDTO> createFeedback(
            @Valid @RequestBody FeedbackDTO request, Authentication authentication) {
        Feedback feedback = ApiDtoMapper.toFeedback(request);
        feedback.setUser(currentUser(authentication));
        return ResponseEntity.status(201).body(
                ApiDtoMapper.toFeedbackResponse(feedbackService.createFeedback(feedback)));
    }

    @GetMapping
    public ResponseEntity<List<FeedbackDTO>> getAllFeedbacks(Authentication authentication) {
        List<Feedback> feedbacks = feedbackService.getAllFeedbacks();
        return ResponseEntity.ok(feedbacks.stream().map(ApiDtoMapper::toFeedbackResponse).toList());
    }

    @GetMapping("/{feedbackId}")
    public ResponseEntity<FeedbackDTO> getFeedbackById(
            @PathVariable Long feedbackId, Authentication authentication) {
        Feedback feedback = feedbackService.getFeedbackById(feedbackId);
        requireAccess(feedback.getUser(), authentication);
        return ResponseEntity.ok(ApiDtoMapper.toFeedbackResponse(feedback));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<FeedbackDTO>> getFeedbacksByUser(
            @PathVariable Long userId, Authentication authentication) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        requireAccess(user, authentication);
        return ResponseEntity.ok(feedbackService.getFeedbacksByUserId(userId).stream()
                .map(ApiDtoMapper::toFeedbackResponse).toList());
    }

    @DeleteMapping("/{feedbackId}")
    public ResponseEntity<Map<String, String>> deleteFeedback(
            @PathVariable Long feedbackId, Authentication authentication) {
        Feedback feedback = feedbackService.getFeedbackById(feedbackId);
        requireAccess(feedback.getUser(), authentication);
        feedbackService.deleteFeedback(feedbackId);
        return ResponseEntity.ok(Map.of("message", "Feedback deleted successfully"));
    }

    /**
     * Administrative response to a feedback entry. Restricted to Admin/SuperAdmin both by
     * SecurityConfig and here, so the authorization is visible at the endpoint itself.
     * The responding user is never taken from the request body.
     */
    @PutMapping("/{feedbackId}/respond")
    public ResponseEntity<FeedbackDTO> respondToFeedback(
            @PathVariable Long feedbackId,
            @Valid @RequestBody RespondToFeedbackDTO request,
            Authentication authentication) {
        if (!isAdmin(authentication)) {
            throw new IllegalStateException("Only administrators can respond to feedback");
        }
        return ResponseEntity.ok(ApiDtoMapper.toFeedbackResponse(
                feedbackService.respondToFeedback(feedbackId, request.adminResponse())));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user no longer exists"));
    }

    private void requireAccess(User user, Authentication authentication) {
        User current = currentUser(authentication);
        boolean allowed = isAdmin(authentication) || user.getUserId().equals(current.getUserId());
        if (!allowed) throw new IllegalStateException("You cannot access this feedback");
    }

    private boolean isAdmin(Authentication a) {
        return a.getAuthorities().stream().anyMatch(x ->
                "ROLE_Admin".equals(x.getAuthority()) || "ROLE_SuperAdmin".equals(x.getAuthority()));
    }

}
