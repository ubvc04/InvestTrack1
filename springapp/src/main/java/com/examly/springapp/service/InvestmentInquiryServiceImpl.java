package com.examly.springapp.service;

import com.examly.springapp.exceptions.InvestmentInquiryException;
import com.examly.springapp.model.InvestmentInquiry;
import com.examly.springapp.repository.InvestmentInquiryRepo;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InvestmentInquiryServiceImpl implements InvestmentInquiryService {

    private final InvestmentInquiryRepo inquiryRepository;
    private final EmailService emailService;

    public InvestmentInquiryServiceImpl(
            InvestmentInquiryRepo inquiryRepository, EmailService emailService) {
        this.inquiryRepository = inquiryRepository;
        this.emailService = emailService;
    }

    @Override
    public InvestmentInquiry createInquiry(InvestmentInquiry investmentInquiry) {
        if (investmentInquiry.getInquiryDate() == null) {
            investmentInquiry.setInquiryDate(LocalDateTime.now());
        }
        investmentInquiry.setStatus("PENDING");
        return inquiryRepository.save(investmentInquiry);
    }

    @Override
    public InvestmentInquiry updateInquiry(Long inquiryId, InvestmentInquiry updatedInquiry) {
        InvestmentInquiry existing = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new InvestmentInquiryException("Inquiry not found with id: " + inquiryId));

        String previousStatus = existing.getStatus() == null
                ? "PENDING" : normalizeStatus(existing.getStatus());
        String requestedStatus = normalizeStatus(updatedInquiry.getStatus());
        if (requestedStatus != null && !requestedStatus.equals(previousStatus)) {
            existing.setStatus(requestedStatus);
        }
        if (updatedInquiry.getMessage() != null) existing.setMessage(updatedInquiry.getMessage());
        if (updatedInquiry.getSubject() != null) existing.setSubject(updatedInquiry.getSubject());
        if (updatedInquiry.getPriority() != null) existing.setPriority(updatedInquiry.getPriority());
        if (updatedInquiry.getAdminResponse() != null) {
            existing.setAdminResponse(updatedInquiry.getAdminResponse());
            existing.setResponseDate(LocalDateTime.now());
        }
        if (updatedInquiry.getContactDetails() != null) existing.setContactDetails(updatedInquiry.getContactDetails());

        InvestmentInquiry saved = inquiryRepository.save(existing);
        if (requestedStatus != null && !requestedStatus.equals(previousStatus)) {
            emailService.sendInquiryStatusUpdateEmail(saved.getUser(), saved, previousStatus, requestedStatus);
        }
        return saved;
    }

    @Override
    public InvestmentInquiry getInquiryById(Long inquiryId) {
        return inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new InvestmentInquiryException("Inquiry not found with id: " + inquiryId));
    }

    @Override
    public List<InvestmentInquiry> getInquiriesByUserId(Long userId) {
        return inquiryRepository.findByUser_UserId(userId);
    }

    @Override
    public List<InvestmentInquiry> getInquiriesByInvestmentId(Long investmentId) {
        return inquiryRepository.findByInvestment_InvestmentId(investmentId);
    }

    @Override
    public List<InvestmentInquiry> getUnresolvedInquiries() {
        return inquiryRepository.findByStatusNot("RESOLVED");
    }

    @Override
    public List<InvestmentInquiry> getInquiriesByPriority(String priority) {
        return inquiryRepository.findByPriority(priority);
    }

    @Override
    public List<InvestmentInquiry> getAllInquiries() {
        return inquiryRepository.findAll();
    }

    @Override
    public void deleteInquiry(Long inquiryId) {
        InvestmentInquiry existing = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new InvestmentInquiryException("Inquiry not found with id: " + inquiryId));
        inquiryRepository.delete(existing);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase();
        if (!normalized.equals("PENDING") && !normalized.equals("RESOLVED")) {
            throw new InvestmentInquiryException("Inquiry status must be PENDING or RESOLVED");
        }
        return normalized;
    }
}