package com.examly.springapp.exceptions;

public class InvestmentException extends RuntimeException {
    public InvestmentException(String message) {
        super(message);
    }
}