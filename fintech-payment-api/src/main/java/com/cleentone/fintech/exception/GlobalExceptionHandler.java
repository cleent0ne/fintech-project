package com.cleentone.fintech.exception;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailExists(EmailAlreadyExistsException e){
        return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("Duplicate Email", e.getMessage()));
    }
    
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredential(InvalidCredentialsException e){

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ErrorResponse("Invalid Credentials", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e){
        String msg = e.getBindingResult().getFieldErrors().stream()
        .map(FieldError :: getDefaultMessage)
        .collect(Collectors.joining("; "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("Validation Failed", msg));
    }

        @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException e){
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(new ErrorResponse("Insufficient Funds", e.getMessage())); 

    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException e){
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("Resource Not Found", e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException e){

        log.warn("Malformed JSON request: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("Invalid Request Body", "The request body is malformed or contains invalid data."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameter(MissingServletRequestParameterException e){

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("Missing Request Parameter", "Parameter '" + e.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e){

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("Invalid Request Parameter", "Invalid value for parameter '" + e.getName() + "'"));

    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e){
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(new ErrorResponse("Method Not Allowed", e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e){
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ErrorResponse("Access Denied","You do not have permission to access this resource."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e, HttpServletRequest request){
        log.error("Unexpected error on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse("Internal Server Error", "An unexpected error occurred. Please try again later."));
    }
}
