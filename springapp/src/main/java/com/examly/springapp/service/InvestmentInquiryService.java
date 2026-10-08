package com.examly.springapp.service;

import com.examly.springapp.model.InvestmentInquiry;
import java.util.List;

public interface InvestmentInquiryService {
    InvestmentInquiry createInquiry(InvestmentInquiry investmentInquiry);
    InvestmentInquiry updateInquiry(Long inquiryId, InvestmentInquiry updatedInquiry);
    InvestmentInquiry getInquiryById(Long inquiryId);
    List<InvestmentInquiry> getInquiriesByUserId(Long userId);
    List<InvestmentInquiry> getInquiriesByInvestmentId(Long investmentId);
    List<InvestmentInquiry> getUnresolvedInquiries();
    List<InvestmentInquiry> getInquiriesByPriority(String priority);
    List<InvestmentInquiry> getAllInquiries();
    void deleteInquiry(Long inquiryId);
}