package com.examly.springapp.service;

import com.examly.springapp.exceptions.DuplicateInvestmentException;
import com.examly.springapp.exceptions.InvestmentException;
import com.examly.springapp.model.Investment;
import com.examly.springapp.model.InvestmentStatus;
import com.examly.springapp.repository.InvestmentRepo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class InvestmentServiceImpl implements InvestmentService {

    private final InvestmentRepo investmentRepository;

    public InvestmentServiceImpl(
            InvestmentRepo investmentRepository) {
        this.investmentRepository = investmentRepository;
    }

    /**
     * Creates an investment after validating the supported status model and the symbol
     * uniqueness rule (one master record per instrument symbol).
     */
    @Override
    public Investment addInvestment(Investment investment) {
        investment.setStatus(requireSupportedStatus(investment.getStatus()));
        investment.setSymbol(normalizeSymbol(investment.getSymbol()));
        requireUniqueSymbol(investment.getSymbol(), null);
        return investmentRepository.save(investment);
    }

    @Override
    public Investment updateInvestment(Long investmentId, Investment updatedInvestment) {
        Investment existing = investmentRepository.findById(investmentId)
                .orElseThrow(() -> new InvestmentException("Investment not found with id: " + investmentId));

        existing.setName(updatedInvestment.getName());
        existing.setDescription(updatedInvestment.getDescription());
        existing.setType(updatedInvestment.getType());
        existing.setPurchasePrice(updatedInvestment.getPurchasePrice());
        existing.setCurrentPrice(updatedInvestment.getCurrentPrice());
        existing.setQuantity(updatedInvestment.getQuantity());
        existing.setPurchaseDate(updatedInvestment.getPurchaseDate());
        existing.setStatus(requireSupportedStatus(updatedInvestment.getStatus()));

        return investmentRepository.save(existing);
    }

    @Override
    public Investment getInvestmentById(Long investmentId) {
        return investmentRepository.findById(investmentId)
                .orElseThrow(() -> new InvestmentException("Investment not found with id: " + investmentId));
    }

    @Override
    public List<Investment> getAllInvestments() {
        return investmentRepository.findAll();
    }

    @Override
    public List<Investment> getInvestmentsByType(String type) {
        return investmentRepository.findByType(type);
    }

    @Override
    public List<Investment> getInvestmentsByStatus(String status) {
        return investmentRepository.findByStatus(status);
    }

    @Override
    public List<Investment> searchInvestments(String keyword) {
        return investmentRepository.findByNameContainingIgnoreCase(keyword);
    }

    @Override
    public void deleteInvestment(Long investmentId) {
        Investment existing = investmentRepository.findById(investmentId)
                .orElseThrow(() -> new InvestmentException("Investment not found with id: " + investmentId));
        investmentRepository.delete(existing);
    }

    /**
     * Rejects anything that is not a supported permanent status and returns the canonical
     * capitalization ("Active" / "Sold") so the API and the UI always agree.
     */
    private String requireSupportedStatus(String status) {
        String canonical = InvestmentStatus.canonicalize(status);
        if (canonical == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Investment status must be Active or Sold");
        }
        return canonical;
    }

    /** Symbols are stored trimmed and upper-cased (tickers are case-insensitive identifiers). */
    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Backend uniqueness validation for investment master records. The symbol is the existing
     * business identifier of an instrument (the schema already declares it unique, together with
     * the seed key); duplicate submissions therefore fail with HTTP 409 instead of a raw
     * database error. A failed check never overwrites or merges existing rows.
     */
    private void requireUniqueSymbol(String symbol, Long ignoringInvestmentId) {
        if (symbol == null || symbol.isBlank()) {
            return;
        }
        Optional<Investment> existing = investmentRepository.findBySymbolIgnoreCase(symbol);
        if (existing.isPresent()
                && (ignoringInvestmentId == null
                || !ignoringInvestmentId.equals(existing.get().getInvestmentId()))) {
            throw new DuplicateInvestmentException(
                    "An investment with symbol '" + symbol + "' already exists.");
        }
    }
}
