package edu.vit.results.web;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Every error leaves the API as {"error": "human readable message"}. */
@RestControllerAdvice
public class ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> api(ApiException e) {
        return error(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        var fe = e.getBindingResult().getFieldError();
        String msg = fe != null ? fe.getDefaultMessage() : "Some fields are invalid";
        return error(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "The request body is malformed or has a wrong value type");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Map<String, String>> mismatch(MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.BAD_REQUEST, "Invalid value for " + e.getName());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException e) {
        return error(HttpStatus.CONFLICT, "That conflicts with existing data (duplicate email, PRN or code?)");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, String>> notFound(NoResourceFoundException e) {
        return error(HttpStatus.NOT_FOUND, "Not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, String>> method(HttpRequestMethodNotSupportedException e) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unexpected(Exception e) {
        log.error("Unhandled error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side");
    }
}
