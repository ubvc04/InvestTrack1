package com.examly.springapp.repository;

import com.examly.springapp.model.InvestmentInquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface InvestmentInquiryRepo extends JpaRepository<InvestmentInquiry, Long> {
    List<InvestmentInquiry> findByUser_UserId(Long userId);
    List<InvestmentInquiry> findByInvestment_InvestmentId(Long investmentId);
    List<InvestmentInquiry> findByStatus(String status);
    List<InvestmentInquiry> findByPriority(String priority);
    List<InvestmentInquiry> findByStatusNot(String status);

    // ---- Admin Console aggregation: workflow counts computed in the database.

    /** Inquiries per stored status (NULL status is counted as PENDING, matching
     *  the application's default): [status, count]. */
    @Query("SELECT COALESCE(i.status, 'PENDING'), COUNT(i) FROM InvestmentInquiry i GROUP BY COALESCE(i.status, 'PENDING')")
    List<Object[]> countInquiriesByStatus();

    /** Inquiries per stored priority: [priority, count]. */
    @Query("SELECT i.priority, COUNT(i) FROM InvestmentInquiry i GROUP BY i.priority")
    List<Object[]> countInquiriesByPriority();

    /** Inquiries created per day (from the stored inquiry timestamp): [date, count]. */
    @Query("SELECT FUNCTION('DATE', i.inquiryDate), COUNT(i) FROM InvestmentInquiry i GROUP BY FUNCTION('DATE', i.inquiryDate)")
    List<Object[]> countInquiriesByDay();

    /** Latest inquiries for the console table. */
    List<InvestmentInquiry> findTop5ByOrderByInquiryDateDesc();
}