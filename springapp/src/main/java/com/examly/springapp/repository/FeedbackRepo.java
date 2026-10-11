package com.examly.springapp.repository;

import com.examly.springapp.model.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FeedbackRepo extends JpaRepository<Feedback, Long> {
    List<Feedback> findByUser_UserId(Long userId);
    List<Feedback> findByInvestment_InvestmentId(Long investmentId);
    List<Feedback> findByUser_UserIdAndInvestment_InvestmentId(Long userId, Long investmentId);

    // ---- Admin Console aggregation: feedback counts computed in the database.

    /** Feedback records per stored category: [category, count]. */
    @Query("SELECT f.category, COUNT(f) FROM Feedback f GROUP BY f.category")
    List<Object[]> countFeedbackByCategory();

    /** Latest feedback entries for the console section. */
    List<Feedback> findTop3ByOrderByFeedbackIdDesc();
}
