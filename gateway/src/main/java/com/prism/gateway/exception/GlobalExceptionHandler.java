package com.prism.gateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<String> handleAuthentication(
            AuthenticationException e) {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(e.getMessage());
    }

    @ExceptionHandler(ModelNotAllowedException.class)
    public ResponseEntity<String> handleModelNotAllowed(
            ModelNotAllowedException e) {

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(e.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<String> handleRateLimit(
            RateLimitExceededException e) {

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(e.getMessage());
    }

    @ExceptionHandler(BudgetExceededException.class)
    public ResponseEntity<String> handleBudgetExceeded(
            BudgetExceededException e) {

        return ResponseEntity
                .status(HttpStatus.PAYMENT_REQUIRED)
                .body(e.getMessage());
    }
}