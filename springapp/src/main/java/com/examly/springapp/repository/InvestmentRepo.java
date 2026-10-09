package com.examly.springapp.repository;

import com.examly.springapp.model.Investment;
import org.springframework.data.jpa.repository.JpaRepository;
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
}