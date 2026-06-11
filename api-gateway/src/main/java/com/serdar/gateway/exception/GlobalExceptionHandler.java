package com.serdar.gateway.exception;

import com.serdar.common.GrpcErrors;
import com.serdar.common.ServiceException;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Bridges gRPC errors and domain {@link ServiceException}s back to HTTP status
 * codes for the clients. Anything else becomes a 500 with a generic message.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String GENERIC_ERROR = "Internal server error";

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<?> handle(ServiceException e) {
        return body(mapStatus(e.code()), e.getMessage());
    }

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<?> handle(StatusRuntimeException e) {
        Exception translated = (Exception) GrpcErrors.toService(e);
        if (translated instanceof ServiceException svc) return handle(svc);
        log.error("Unhandled gRPC error", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handle(Exception e) {
        log.error("Unhandled request error", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_ERROR);
    }

    private static HttpStatus mapStatus(ServiceException.Code c) {
        return switch (c) {
            case NOT_FOUND           -> HttpStatus.NOT_FOUND;
            case CONFLICT            -> HttpStatus.CONFLICT;
            case INVALID_ARGUMENT    -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED     -> HttpStatus.UNAUTHORIZED;
            case PERMISSION_DENIED   -> HttpStatus.FORBIDDEN;
            case FAILED_PRECONDITION -> HttpStatus.PRECONDITION_FAILED;
            case INTERNAL            -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message == null ? status.getReasonPhrase() : message));
    }
}
