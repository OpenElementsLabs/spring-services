package com.openelements.spring.base.services.altcha;

import com.openelements.spring.base.problem.ProblemFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers a failed bot check.
 *
 * <p>With RFC 9457 problem details enabled — the default — the response is a {@code 400} problem of the
 * matching {@link AltchaProblemType}, carrying the visitor's message in the language of the request as
 * {@code detail}:
 *
 * <pre>{@code
 * {
 *   "type": "https://open-elements.com/problems/spring-services/altcha-expired",
 *   "title": "Bot check expired",
 *   "status": 400,
 *   "detail": "Die Prüfung ist abgelaufen. Bitte versuchen Sie es erneut.",
 *   "instance": "/api/contact",
 *   "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
 * }
 * }</pre>
 *
 * <p>With {@code openelements.problem-details.enabled=false} there is no {@link ProblemFactory} bean, and
 * the handler answers {@code {"code": "altcha-expired", "message": …}} exactly as it did before. The
 * stable identifier survives the switch either way: it is the {@code code} in the old body and the last
 * segment of the {@code type} URI in the new one.
 *
 * <p>The advice applies to every controller, because the exception is thrown by the application's own
 * endpoints. An application with another error format registers its own bean of this type and the library
 * backs off.
 */
@RestControllerAdvice
public class AltchaExceptionHandler {

    private final ObjectProvider<ProblemFactory> problemFactory;

    /**
     * Creates the handler.
     *
     * @param problemFactory provider for the optional {@link ProblemFactory} bean; absent while
     *                       {@code openelements.problem-details.enabled=false}
     */
    public AltchaExceptionHandler(final ObjectProvider<ProblemFactory> problemFactory) {
        this.problemFactory = Objects.requireNonNull(problemFactory, "problemFactory must not be null");
    }

    /**
     * Renders the failed check.
     *
     * @param e       the failed check
     * @param request the request being handled, for {@code instance} and {@code traceId}
     * @return status 400, as an RFC 9457 problem or — without problem details — as the previous
     *         {@code {code, message}} body
     */
    @ExceptionHandler(AltchaVerificationException.class)
    public ResponseEntity<?> handle(final AltchaVerificationException e, final HttpServletRequest request) {
        final ProblemFactory factory = problemFactory.getIfAvailable();
        if (factory == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", e.code(), "message", String.valueOf(e.getMessage())));
        }
        final ProblemDetail problem = factory.create(AltchaProblemType.forResult(e.result()),
                e.getMessage(), request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }
}
