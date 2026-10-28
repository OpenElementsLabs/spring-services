package com.openelements.spring.base.services.altcha;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers a failed bot check with status 400 and {@code {"code": "altcha-missing", "message": …}}.
 *
 * <p>Applies to every controller, because the exception is thrown by the application's own endpoints.
 * An application with another error format registers its own bean of this type and the library backs
 * off.
 */
@RestControllerAdvice
public class AltchaExceptionHandler {

    /** Creates the handler. */
    public AltchaExceptionHandler() {
    }

    /**
     * Renders the failed check.
     *
     * @param e the failed check
     * @return status 400 with the stable code and the message in the language of the request
     */
    @ExceptionHandler(AltchaVerificationException.class)
    public ResponseEntity<Map<String, String>> handle(final AltchaVerificationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("code", e.code(), "message", String.valueOf(e.getMessage())));
    }
}
