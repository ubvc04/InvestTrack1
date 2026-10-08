package com.examly.springapp.repository;

import com.examly.springapp.model.Investment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvestmentRepo extends JpaRepository<Investment, Long> {
    List<Investment> findByType(String type);
    List<Investment> findByStatus(String status);
    List<Investment> findByNameContainingIgnoreCase(String keyword);
    Optional<Investment> findBySymbol(String symbol);
}