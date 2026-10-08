package com.examly.springapp.service;

import com.examly.springapp.model.Investment;
import java.util.List;

public interface InvestmentService {
    Investment addInvestment(Investment investment);
    Investment updateInvestment(Long investmentId, Investment updatedInvestment);
    Investment getInvestmentById(Long investmentId);
    List<Investment> getAllInvestments();
    List<Investment> getInvestmentsByType(String type);
    List<Investment> getInvestmentsByStatus(String status);
    List<Investment> searchInvestments(String keyword);
    void deleteInvestment(Long investmentId);
}