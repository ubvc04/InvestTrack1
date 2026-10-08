package com.examly.springapp.controller;

import com.examly.springapp.dto.ApiDtoMapper;
import com.examly.springapp.dto.InvestmentInquiryDTO;
import com.examly.springapp.model.InvestmentInquiry;
import com.examly.springapp.model.User;
import com.examly.springapp.repository.UserRepo;
import com.examly.springapp.service.InvestmentInquiryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inquiries")
@CrossOrigin(origins = "http://localhost:8081")
public class InvestmentInquiryController {
    private final InvestmentInquiryService inquiryService;
    private final UserRepo userRepository;

    public InvestmentInquiryController(InvestmentInquiryService inquiryService, UserRepo userRepository) {
        this.inquiryService = inquiryService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<InvestmentInquiryDTO> addInquiry(
            @Valid @RequestBody InvestmentInquiryDTO request, Authentication authentication) {
        InvestmentInquiry inquiry = ApiDtoMapper.toInquiry(request);
        inquiry.setUser(currentUser(authentication));
        return ResponseEntity.status(201).body(
                ApiDtoMapper.toInquiryResponse(inquiryService.createInquiry(inquiry)));
    }

    @GetMapping
    public ResponseEntity<List<InvestmentInquiryDTO>> getAllInquiries(Authentication authentication) {
        List<InvestmentInquiry> inquiries = inquiryService.getAllInquiries();
        return ResponseEntity.ok(inquiries.stream().map(ApiDtoMapper::toInquiryResponse).toList());
    }

    @GetMapping("/{inquiryId}")
    public ResponseEntity<InvestmentInquiryDTO> getInquiryById(
            @PathVariable Long inquiryId, Authentication authentication) {
        InvestmentInquiry inquiry = inquiryService.getInquiryById(inquiryId);
        requireAccess(inquiry.getUser(), authentication);
        return ResponseEntity.ok(ApiDtoMapper.toInquiryResponse(inquiry));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<InvestmentInquiryDTO>> getInquiriesByUser(
            @PathVariable Long userId, Authentication authentication) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        requireAccess(user, authentication);
        return ResponseEntity.ok(inquiryService.getInquiriesByUserId(userId).stream()
                .map(ApiDtoMapper::toInquiryResponse).toList());
    }

    @PutMapping("/{inquiryId}")
    public ResponseEntity<InvestmentInquiryDTO> updateInquiry(
            @PathVariable Long inquiryId, @Valid @RequestBody InvestmentInquiryDTO request,
            Authentication authentication) {
        InvestmentInquiry existing = inquiryService.getInquiryById(inquiryId);
        if (!isAdmin(authentication) || !canAccess(existing.getUser(), authentication)) {
            throw new IllegalStateException("You cannot update this inquiry");
        }
        return ResponseEntity.ok(ApiDtoMapper.toInquiryResponse(
                inquiryService.updateInquiry(inquiryId, ApiDtoMapper.toInquiry(request))));
    }

    @DeleteMapping("/{inquiryId}")
    public ResponseEntity<Map<String, String>> deleteInquiry(
            @PathVariable Long inquiryId, Authentication authentication) {
        InvestmentInquiry inquiry = inquiryService.getInquiryById(inquiryId);
        requireAccess(inquiry.getUser(), authentication);
        inquiryService.deleteInquiry(inquiryId);
        return ResponseEntity.ok(Map.of("message", "Inquiry deleted successfully"));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user no longer exists"));
    }

    private void requireAccess(User user, Authentication authentication) {
        if (!canAccess(user, authentication)) throw new IllegalStateException("You cannot access this inquiry");
    }

    private boolean canAccess(User user, Authentication authentication) {
        User current = currentUser(authentication);
        return isAdmin(authentication) || user.getUserId().equals(current.getUserId());
    }

    private boolean isAdmin(Authentication a) {
        return a.getAuthorities().stream().anyMatch(x ->
                "ROLE_Admin".equals(x.getAuthority()) || "ROLE_SuperAdmin".equals(x.getAuthority()));
    }

}
