package kr.ac.dongguk.individualresearch.common;

import kr.ac.dongguk.individualresearch.auth.AuthException;
import kr.ac.dongguk.individualresearch.application.ApplicationConflictException;
import kr.ac.dongguk.individualresearch.application.ApplicationForbiddenException;
import kr.ac.dongguk.individualresearch.application.ApplicationFlowException;
import kr.ac.dongguk.individualresearch.application.ApplicationNotFoundException;
import kr.ac.dongguk.individualresearch.application.file.ApplicationFileException;
import kr.ac.dongguk.individualresearch.document.DocumentException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApplicationFileException.class)
    public ResponseEntity<ApiResponse<Void>> handleApplicationFile(ApplicationFileException exception) {
        HttpStatus status = switch (exception.errorCode()) {
            case "APPLICATION_FILE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "APPLICATION_FILE_FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "APPLICATION_FILE_DUPLICATE" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(ApiResponse.fail(exception.errorCode(), exception.getMessage()));
    }
    @ExceptionHandler(ApplicationFlowException.class)
    public ResponseEntity<ApiResponse<Void>> handleApplicationFlow(ApplicationFlowException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(exception.errorCode(), exception.getMessage()));
    }
    @ExceptionHandler(DocumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleDocument(DocumentException exception) {
        HttpStatus status = switch (exception.errorCode()) {
            case "DRAFT_NOT_FOUND", "TEMPLATE_NOT_FOUND", "DOCUMENT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "DUPLICATE_TEMPLATE" -> HttpStatus.CONFLICT;
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(ApiResponse.fail(exception.errorCode(), exception.getMessage()));
    }
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail(exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(exception.getMessage()));
    }

    @ExceptionHandler(ApplicationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ApplicationNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("APPLICATION_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(ApplicationConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ApplicationConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail(exception.getMessage()));
    }

    @ExceptionHandler(ApplicationForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ApplicationForbiddenException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail("APPLICATION_FORBIDDEN", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.fail("요청 값을 확인해 주세요."));
    }
}
