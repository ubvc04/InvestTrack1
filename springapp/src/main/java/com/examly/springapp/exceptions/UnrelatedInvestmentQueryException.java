package com.examly.springapp.exceptions;

public class UnrelatedInvestmentQueryException extends RuntimeException {

    public UnrelatedInvestmentQueryException(String message) {
        super(message);
    }
}