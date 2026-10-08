package com.examly.springapp.repository;

import com.examly.springapp.model.InvestmentInquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface InvestmentInquiryRepo extends JpaRepository<InvestmentInquiry, Long> {
    List<InvestmentInquiry> findByUser_UserId(Long userId);
    List<InvestmentInquiry> findByInvestment_InvestmentId(Long investmentId);
    List<InvestmentInquiry> findByStatus(String status);
    List<InvestmentInquiry> findByPriority(String priority);
    List<InvestmentInquiry> findByStatusNot(String status);
}