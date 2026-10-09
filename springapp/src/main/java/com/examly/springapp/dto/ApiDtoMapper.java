package com.examly.springapp.dto;

import com.examly.springapp.model.Feedback;
import com.examly.springapp.model.Investment;
import com.examly.springapp.model.InvestmentInquiry;
import com.examly.springapp.model.User;
import java.util.Locale;

public final class ApiDtoMapper {
    private ApiDtoMapper() {}

    public static User toUser(UserRequestDTO dto) {
        User user = new User();
        user.setUsername(dto.username());
        user.setEmail(dto.email());
        user.setPassword(dto.password());
        user.setMobileNumber(dto.mobileNumber());
        user.setUserRole(dto.userRole());
        return user;
    }

    public static UserResponseDTO toUserResponse(User user) {
        return new UserResponseDTO(user.getUserId(), user.getUsername(), user.getEmail(),
                user.getMobileNumber(), user.getUserRole(), user.isMustChangePassword());
    }

    public static Investment toInvestment(InvestmentDTO dto) {
        Investment investment = new Investment();
        investment.setInvestmentId(dto.investmentId());
        investment.setName(dto.name());
        investment.setSymbol(dto.symbol());
        investment.setExchange(dto.exchange());
        investment.setMarket(dto.market());
        investment.setAssetClass(dto.assetClass());
        investment.setCurrency(dto.currency());
        investment.setDescription(dto.description());
        investment.setType(dto.type());
        investment.setPurchasePrice(dto.purchasePrice());
        investment.setCurrentPrice(dto.currentPrice());
        investment.setQuantity(dto.quantity());
        investment.setPurchaseDate(dto.purchaseDate());
        investment.setStatus(dto.status());
        return investment;
    }

    public static InvestmentDTO toInvestmentResponse(Investment i) {
        return new InvestmentDTO(i.getInvestmentId(), i.getName(), i.getSymbol(), i.getExchange(),
                i.getMarket(), i.getAssetClass(), i.getCurrency(), i.getDescription(), i.getType(),
                i.getPurchasePrice(), i.getCurrentPrice(), i.getQuantity(), i.getPurchaseDate(), i.getStatus());
    }

    public static Feedback toFeedback(FeedbackDTO dto) {
        Feedback feedback = new Feedback();
        feedback.setFeedbackId(dto.feedbackId());
        feedback.setFeedbackText(dto.feedbackText());
        feedback.setDate(dto.date());
        feedback.setCategory(dto.category());
        feedback.setSubject(dto.subject());
        if (dto.investment() != null) feedback.setInvestment(toInvestment(dto.investment()));
        return feedback;
    }

    public static FeedbackDTO toFeedbackResponse(Feedback f) {
        return new FeedbackDTO(f.getFeedbackId(), f.getFeedbackText(), f.getDate(),
                f.getUser() == null ? null : toUserResponse(f.getUser()),
                f.getInvestment() == null ? null : toInvestmentResponse(f.getInvestment()),
                f.getCategory(), f.getSubject(), f.getAdminResponse(), f.getResponseDate());
    }

    public static InvestmentInquiry toInquiry(InvestmentInquiryDTO dto) {
        InvestmentInquiry inquiry = new InvestmentInquiry();
        inquiry.setInquiryId(dto.inquiryId());
        inquiry.setInvestment(toInvestment(dto.investment()));
        inquiry.setMessage(dto.message());
        inquiry.setSubject(dto.subject());
        inquiry.setStatus(dto.status());
        inquiry.setPriority(dto.priority());
        inquiry.setInquiryDate(dto.inquiryDate());
        inquiry.setResponseDate(dto.responseDate());
        inquiry.setAdminResponse(dto.adminResponse());
        inquiry.setContactDetails(dto.contactDetails());
        return inquiry;
    }

    public static InvestmentInquiryDTO toInquiryResponse(InvestmentInquiry i) {
        return new InvestmentInquiryDTO(i.getInquiryId(),
                i.getUser() == null ? null : toUserResponse(i.getUser()),
                toInvestmentResponse(i.getInvestment()), i.getMessage(), i.getSubject(),
                i.getStatus() == null ? "PENDING" : i.getStatus().toUpperCase(Locale.ROOT), i.getPriority(),
                i.getInquiryDate(), i.getResponseDate(), i.getAdminResponse(), i.getContactDetails());
    }
}
