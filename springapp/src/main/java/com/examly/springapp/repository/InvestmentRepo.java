package com.examly.springapp.repository;

import com.examly.springapp.model.Investment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvestmentRepo extends JpaRepository<Investment, Long> {
    List<Investment> findByType(String type);
    List<Investment> findByStatus(String status);
    List<Investment> findByNameContainingIgnoreCase(String keyword);
    Optional<Investment> findBySymbol(String symbol);

    /** Case-insensitive lookup used for backend uniqueness validation. */
    Optional<Investment> findBySymbolIgnoreCase(String symbol);

    // Seed tracking: used by the startup seeder to insert only missing records.
    List<Investment> findBySeedKeyIn(Collection<String> seedKeys);
    List<Investment> findBySymbolIn(Collection<String> symbols);

    // ---- Admin Console aggregation: counts are computed in the database so the
    // ---- dashboard never needs to download every record to summarise it.

    /** Active catalogue entries (status matches case-insensitively). */
    long countByStatusIgnoreCase(String status);

    /** Number of investments per asset type: [type, count]. */
    @Query("SELECT i.type, COUNT(i) FROM Investment i GROUP BY i.type")
    List<Object[]> countInvestmentsByType();

    /** Investments created per purchase-date month (purchaseDate is yyyy-MM-dd):
     *  [yyyy-MM, count]. */
    @Query("SELECT SUBSTRING(i.purchaseDate, 1, 7), COUNT(i) FROM Investment i GROUP BY SUBSTRING(i.purchaseDate, 1, 7)")
    List<Object[]> countInvestmentsByMonth();

    /** Latest catalogue entries for the console snapshot. */
    List<Investment> findTop5ByOrderByInvestmentIdDesc();
}