package com.examly.springapp.service;

import com.examly.springapp.exceptions.InvestmentInquiryException;
import com.examly.springapp.model.InvestmentInquiry;
import com.examly.springapp.repository.InvestmentInquiryRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InvestmentInquiryServiceImpl implements InvestmentInquiryService {

    private static final Logger logger = LoggerFactory.getLogger(InvestmentInquiryServiceImpl.class);

    private final InvestmentInquiryRepo inquiryRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;

    public InvestmentInquiryServiceImpl(
            InvestmentInquiryRepo inquiryRepository, EmailService emailService,
            NotificationService notificationService) {
        this.inquiryRepository = inquiryRepository;
        this.emailService = emailService;
        this.notificationService = notificationService;
    }

    @Override
    public InvestmentInquiry createInquiry(InvestmentInquiry investmentInquiry) {
        if (investmentInquiry.getInquiryDate() == null) {
            investmentInquiry.setInquiryDate(LocalDateTime.now());
        }
        investmentInquiry.setStatus("PENDING");
        InvestmentInquiry saved = inquiryRepository.save(investmentInquiry);
        notifyAdministratorsOfNewInquiry(saved);
        return saved;
    }

    /**
     * One notification per intended administrator, created only after the inquiry row exists.
     * The event key is derived from the inquiry id, so a repeated submission of the same
     * inquiry cannot generate duplicate administrator notifications.
     */
    private void notifyAdministratorsOfNewInquiry(InvestmentInquiry inquiry) {
        String who = inquiry.getUser() == null ? "A user"
                : (inquiry.getUser().getUsername() != null && !inquiry.getUser().getUsername().isBlank()
                ? inquiry.getUser().getUsername() : inquiry.getUser().getEmail());
        String subjectPart = inquiry.getSubject() == null || inquiry.getSubject().isBlank()
                ? "" : " — " + inquiry.getSubject();
        String investmentPart = inquiry.getInvestment() != null
                ? " about " + inquiry.getInvestment().getName() : "";
        String message = who + " submitted an inquiry" + subjectPart + investmentPart
                + ": \"" + abbreviate(inquiry.getMessage(), 240) + "\"";
        notificationService.notifyAdministrators(
                "INQUIRY",
                "New investment inquiry",
                message,
                inquiry.getInquiryId(),
                null,
                NotificationService.eventKey("inquiry-created", inquiry.getInquiryId()));
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
        boolean responseAdded = false;
        if (updatedInquiry.getAdminResponse() != null) {
            existing.setAdminResponse(updatedInquiry.getAdminResponse());
            existing.setResponseDate(LocalDateTime.now());
            responseAdded = true;
        }
        if (updatedInquiry.getContactDetails() != null) existing.setContactDetails(updatedInquiry.getContactDetails());

        boolean statusChanged = requestedStatus != null && !requestedStatus.equals(previousStatus);
        InvestmentInquiry saved = inquiryRepository.save(existing);

        if (statusChanged || responseAdded) {
            notifyOwnerOfUpdate(saved, previousStatus, requestedStatus, statusChanged, responseAdded);
        }
        if (statusChanged) {
            // The e-mail channel stays best effort: a mail transport problem must not undo a
            // saved response or the in-app notification that was already created above.
            try {
                emailService.sendInquiryStatusUpdateEmail(saved.getUser(), saved,
                        previousStatus, requestedStatus);
            } catch (RuntimeException exception) {
                logger.warn("Inquiry status email could not be sent for inquiry {}; "
                        + "the in-app notification was still created", saved.getInquiryId(), exception);
            }
        }
        return saved;
    }

    /**
     * Notifies the user who submitted the inquiry when an administrator responds or changes
     * the status. Created only after the update was saved, and idempotent per
     * (inquiry, status, response) so a repeated identical submission cannot duplicate it.
     */
    private void notifyOwnerOfUpdate(InvestmentInquiry inquiry, String previousStatus,
                                     String requestedStatus, boolean statusChanged,
                                     boolean responseAdded) {
        if (inquiry.getUser() == null) {
            return;
        }
        StringBuilder message = new StringBuilder();
        if (responseAdded) {
            message.append("An administrator replied to your inquiry");
            if (inquiry.getSubject() != null && !inquiry.getSubject().isBlank()) {
                message.append(" — ").append(inquiry.getSubject());
            }
            message.append(": \"").append(abbreviate(inquiry.getAdminResponse(), 300)).append('"');
        }
        if (statusChanged) {
            if (message.length() > 0) {
                message.append(' ');
            }
            message.append("Status changed from ").append(previousStatus)
                    .append(" to ").append(requestedStatus).append('.');
        }

        String title = responseAdded ? "Inquiry response" : "Inquiry status update";
        // Keyed on the resulting (status, response) content — not on change flags — so a
        // repeated identical submission produces the same key and is de-duplicated, while
        // any genuinely new status or edited response still notifies the owner.
        String eventKey = NotificationService.eventKey("inquiry-update", inquiry.getInquiryId(),
                inquiry.getStatus() == null ? "" : inquiry.getStatus(),
                responseAdded && inquiry.getAdminResponse() != null
                        ? Integer.toHexString(inquiry.getAdminResponse().hashCode()) : "");

        notificationService.notifyUser(inquiry.getUser(), "INQUIRY", title, message.toString(),
                inquiry.getInquiryId(), null, eventKey);
    }

    private String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        String singleLine = value.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= max ? singleLine : singleLine.substring(0, max) + "…";
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