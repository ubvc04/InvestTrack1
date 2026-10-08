package com.examly.springapp.exceptions;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvestmentException.class)
    public ResponseEntity<String> handleInvestmentException(
            InvestmentException ex) {

        return ResponseEntity.status(404)
                .body(ex.getMessage());
    }

    @ExceptionHandler(InvestmentInquiryException.class)
    public ResponseEntity<String> handleInvestmentInquiryException(
            InvestmentInquiryException ex) {

        return ResponseEntity.status(404)
                .body(ex.getMessage());
    }

    @ExceptionHandler(DuplicateInvestmentException.class)
    public ResponseEntity<String> handleDuplicateInvestmentException(
            DuplicateInvestmentException ex) {

        return ResponseEntity.status(409)
                .body(ex.getMessage());
    }

    @ExceptionHandler(UnrelatedInvestmentQueryException.class)
    public ResponseEntity<String> handleUnrelatedInvestmentQueryException(
            UnrelatedInvestmentQueryException ex) {

        return ResponseEntity.status(400)
                .body(ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(
            ResponseStatusException ex) {

        return ResponseEntity.status(ex.getStatusCode())
                .body(ex.getReason());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationException(
            MethodArgumentNotValidException ex) {

        StringBuilder errors = new StringBuilder();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.append(error.getField())
                              .append(": ")
                              .append(error.getDefaultMessage())
                              .append("\n"));

        return ResponseEntity.status(400)
                .body(errors.toString());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalStateException(
            IllegalStateException ex) {

        return ResponseEntity.status(403)
                .body(ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(
            RuntimeException ex) {

        return ResponseEntity.status(400)
                .body(ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(
            Exception ex) {

        return ResponseEntity.status(500)
                .body("Something went wrong");
    }
}