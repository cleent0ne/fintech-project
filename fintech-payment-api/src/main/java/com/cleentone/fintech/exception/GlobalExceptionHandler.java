package com.cleentone.fintech.exception;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is our safety net. Whenever something goes wrong anywhere in the 
 * application, it usually ends up here so we can return a consistent, 
 * human-friendly JSON error message to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Someone tried to register with an email that's already in our database.
     */
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailExists(EmailAlreadyExistsException e){
        return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("Duplicate Email", e.getMessage()));
    }
    
    /**
     * Wrong email or password during login.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredential(InvalidCredentialsException e){
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ErrorResponse("Invalid Credentials", e.getMessage()));
    }

    /**
     * The client sent data that failed our validation rules (like a missing field).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e){
        String msg = e.getBindingResult().getFieldErrors().stream()
        .map(FieldError :: getDefaultMessage)
        .collect(Collectors.joining("; "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("VALIDATION_FAILED", msg));
    }

    /**
     * A transfer was attempted but there wasn't enough money in the wallet.
     */
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException e){
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(new ErrorResponse("INSUFFICIENT_FUNDS", e.getMessage())); 
    }

    /**
     * We couldn't find the resource (user, wallet, etc.) they were looking for.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException e){
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("NOT_FOUND", e.getMessage()));
    }

    /**
     * The JSON sent by the client was broken or malformed.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException e){
        log.warn("Malformed JSON request: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("Invalid Request Body", "The request body is malformed or contains invalid data."));
    }

    /**
     * A required URL parameter was missing.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameter(MissingServletRequestParameterException e){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("Missing Request Parameter", "Parameter '" + e.getParameterName() + "' is required."));
    }

    /**
     * A parameter was provided but it's the wrong type (like sending text where a number belongs).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("Invalid Request Parameter", "Invalid value for parameter '" + e.getName() + "'"));
    }

    /**
     * Trying to use an HTTP method (like GET) on an endpoint that only supports POST.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e){
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(new ErrorResponse("METHOD_NOT_ALLOWED", e.getMessage()));
    }

    /**
     * The user is logged in but doesn't have the right permissions for this action.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e){
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ErrorResponse("Access Denied","You do not have permission to access this resource."));
    }

    /**
     * Our last resort for any errors we didn't explicitly catch above.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e, HttpServletRequest request){
        log.error("Unexpected error on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse("Internal Server Error", "An unexpected error occurred. Please try again later."));
    }

    /**
     * Something was wrong with a transfer request (e.g., negative amount).
     */
    @ExceptionHandler(InvalidTransferException.class)
    public ResponseEntity<ErrorResponse> invalidTransfer(InvalidTransferException e){
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(new ErrorResponse("INVALID_TRANSFER", e.getMessage()));
    }

    /**
     * The user tried to hit a URL that doesn't exist at all.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleRouteNotFound(
            NoHandlerFoundException ex, HttpServletRequest request) {
        log.warn("Route not found: {} {}", ex.getHttpMethod(), request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                    "ROUTE_NOT_FOUND",
                    "The endpoint '" + request.getRequestURI() + "' does not exist"
                ));
    }

    /**
     * The client didn't send application/json.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ErrorResponse(
                    "UNSUPPORTED_MEDIA_TYPE",
                    "Content-Type must be application/json"
                ));
    }

    /**
     * The requested payment was not found in the database.
     */
    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotFound(PaymentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("PAYMENT_NOT_FOUND", e.getMessage()));
    }

    /**
     * An idempotency conflict occurred (duplicate payment details).
     */
    @ExceptionHandler(DuplicatePaymentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicatePayment(DuplicatePaymentException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("DUPLICATE_PAYMENT", e.getMessage()));
    }

    /**
     * The payment is in an invalid status for the requested transition action.
     */
    @ExceptionHandler(InvalidPaymentStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPaymentState(InvalidPaymentStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("INVALID_PAYMENT_STATE", e.getMessage()));
    }
}
