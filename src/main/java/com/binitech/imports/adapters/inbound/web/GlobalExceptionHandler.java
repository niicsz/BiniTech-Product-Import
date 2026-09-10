package com.binitech.imports.adapters.inbound.web;

import com.binitech.imports.adapters.inbound.web.generated.model.ErrorDTO;
import com.binitech.imports.domain.exception.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ResourceNotFoundException.class)
  ResponseEntity<ErrorDTO> notFound(ResourceNotFoundException exception) {
    return error(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
  }

  @ExceptionHandler({
    BusinessException.class,
    IllegalArgumentException.class,
    MethodArgumentNotValidException.class
  })
  ResponseEntity<ErrorDTO> badRequest(Exception exception) {
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ErrorDTO> tooLarge(Exception exception) {
    return error(
        HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "O arquivo excede o limite permitido.");
  }

  @ExceptionHandler(ExternalServiceUnavailableException.class)
  ResponseEntity<ErrorDTO> unavailable(Exception exception) {
    return error(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", exception.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ErrorDTO> forbidden(AccessDeniedException exception) {
    return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", exception.getMessage());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorDTO> generic(Exception exception) {
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "Erro interno ao processar a importação.");
  }

  private ResponseEntity<ErrorDTO> error(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status)
        .body(
            new ErrorDTO()
                .code(code)
                .message(message)
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC)));
  }
}
