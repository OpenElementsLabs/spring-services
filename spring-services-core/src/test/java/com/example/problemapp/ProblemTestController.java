package com.example.problemapp;

import com.openelements.spring.base.problem.ProblemException;
import com.openelements.spring.base.problem.ProblemType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * One endpoint per error the problem-details feature has to render.
 *
 * <p>Deliberately an ordinary {@code @RestController} with no error handling of its own: everything
 * these endpoints produce has to be turned into a problem by the library alone, which is what the
 * tests assert.
 */
@RestController
public class ProblemTestController {

  /** An application problem type, as an application would define it. */
  public static final ProblemType DUPLICATE_RECORD =
      ProblemType.of("duplicate-record", HttpStatus.CONFLICT, "Duplicate record");

  /** An application problem type with a server-error status, for the logging assertions. */
  public static final ProblemType UPSTREAM_DOWN =
      ProblemType.of("upstream-down", HttpStatus.SERVICE_UNAVAILABLE, "Upstream down");

  private final ProblemTestService service;

  ProblemTestController(final ProblemTestService service) {
    this.service = service;
  }

  @GetMapping("/api/test/ok")
  String ok() {
    return "ok";
  }

  @GetMapping("/api/test/fail")
  String fail() {
    throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  }

  @GetMapping("/api/test/status/{code}")
  String status(@PathVariable final int code) {
    throw new ResponseStatusException(HttpStatusCode.valueOf(code));
  }

  @GetMapping("/api/test/not-found-with-reason")
  String notFoundWithReason() {
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact not found");
  }

  /** The exact shape {@code TranslationService} throws when an upstream call fails. */
  @GetMapping("/api/test/bad-gateway")
  String badGateway() {
    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Translation API call failed");
  }

  @GetMapping("/api/test/response-status")
  String responseStatus() {
    throw new AlreadyTakenException();
  }

  @GetMapping("/api/test/problem")
  String problem() {
    throw new ProblemException(DUPLICATE_RECORD, "Record already exists");
  }

  @GetMapping("/api/test/problem-without-detail")
  String problemWithoutDetail() {
    throw new ProblemException(DUPLICATE_RECORD);
  }

  @GetMapping("/api/test/problem-with-extension")
  String problemWithExtension() {
    throw new ProblemException(
        DUPLICATE_RECORD, "Record already exists", java.util.Map.of("existingId", "42"));
  }

  @GetMapping("/api/test/problem-subclass")
  String problemSubclass() {
    throw new DuplicateRecordException();
  }

  @GetMapping("/api/test/problem-enum")
  String problemEnum() {
    throw new ProblemException(CrmProblemType.DUPLICATE_CONTACT);
  }

  @GetMapping("/api/test/problem-5xx")
  String problem5xx() {
    throw new ProblemException(UPSTREAM_DOWN, "The upstream registry is not answering");
  }

  @GetMapping("/api/test/boom")
  String boom() {
    throw new IllegalStateException("SELECT * FROM users WHERE email='jane@example.com'");
  }

  @PostMapping(value = "/api/test/body", consumes = MediaType.APPLICATION_JSON_VALUE)
  String body(@Valid @RequestBody final ProblemTestBodies.Order order) {
    return "ok";
  }

  @PostMapping(value = "/api/test/range", consumes = MediaType.APPLICATION_JSON_VALUE)
  String range(@Valid @RequestBody final ProblemTestBodies.DateRange range) {
    return "ok";
  }

  @PostMapping(value = "/api/test/body-and-param", consumes = MediaType.APPLICATION_JSON_VALUE)
  String bodyAndParam(
      @Valid @RequestBody final ProblemTestBodies.Simple simple,
      @RequestParam @Max(100) final int size) {
    return "ok";
  }

  @GetMapping("/api/test/param")
  String param(@RequestParam @Max(100) final int size) {
    return "ok";
  }

  @GetMapping("/api/test/path-and-header/{id}")
  String pathAndHeader(
      @PathVariable @Min(1) final long id, @RequestHeader("X-Page") @Min(1) final int page) {
    return "ok";
  }

  @GetMapping("/api/test/typed")
  String typed(@RequestParam final int size) {
    return "ok";
  }

  @GetMapping("/api/test/only-get")
  String onlyGet() {
    return "ok";
  }

  /** Calls a {@code @Validated} service with an argument it builds itself — a server-side defect. */
  @GetMapping("/api/test/service-violation")
  String serviceViolation() {
    service.process(5000);
    return "ok";
  }

  /** Stands in for an application exception that carries its own status. */
  @ResponseStatus(value = HttpStatus.CONFLICT, reason = "Already taken")
  static final class AlreadyTakenException extends RuntimeException {}

  /** Stands in for an application's domain exception built on {@link ProblemException}. */
  static final class DuplicateRecordException extends ProblemException {
    DuplicateRecordException() {
      super(DUPLICATE_RECORD, "Record already exists");
    }
  }

  /** Stands in for an application that lists its problem types as an enum. */
  public enum CrmProblemType implements ProblemType {

    /** A contact with the same e-mail address is already stored. */
    DUPLICATE_CONTACT("duplicate-contact", HttpStatus.CONFLICT, "Duplicate contact");

    private final String slug;
    private final HttpStatus status;
    private final String title;

    CrmProblemType(final String slug, final HttpStatus status, final String title) {
      this.slug = slug;
      this.status = status;
      this.title = title;
    }

    @Override
    public String slug() {
      return slug;
    }

    @Override
    public HttpStatus status() {
      return status;
    }

    @Override
    public String title() {
      return title;
    }
  }
}
