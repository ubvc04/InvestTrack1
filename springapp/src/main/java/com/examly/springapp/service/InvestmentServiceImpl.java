package com.examly.springapp.service;

import com.examly.springapp.exceptions.InvestmentException;
import com.examly.springapp.model.Investment;
import com.examly.springapp.repository.InvestmentRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InvestmentServiceImpl implements InvestmentService {

    private final InvestmentRepo investmentRepository;

    public InvestmentServiceImpl(
            InvestmentRepo investmentRepository) {
        this.investmentRepository = investmentRepository;
    }

    @Override
    public Investment addInvestment(Investment investment) {
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
        existing.setStatus(updatedInvestment.getStatus());

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
}