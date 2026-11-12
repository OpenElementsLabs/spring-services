/**
 * RFC 9457 problem details for every error response.
 *
 * <p>Every error an application built on {@code spring-services} produces — a {@code 401} from the
 * security chain, a validation failure, an unparseable body, an unhandled exception, an error the
 * servlet container raised on its own — is answered with {@code Content-Type:
 * application/problem+json} and a body of the shape
 *
 * <pre>{@code
 * {
 *   "type": "https://open-elements.com/problems/spring-services/validation-failed",
 *   "title": "Validation failed",
 *   "status": 400,
 *   "detail": "Invalid query parameter 'size': must be less than or equal to 100.",
 *   "instance": "/api/contacts",
 *   "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
 *   "errors": [
 *     { "pointer": "/address/postal_code", "message": "must match \"[0-9]{5}\"" }
 *   ]
 * }
 * }</pre>
 *
 * <p>The format follows the GovStack API Design Guide, chapter 11, so an application can implement a
 * GovStack building block without writing an error layer of its own. SCIM keeps its RFC 7644
 * envelope and is untouched by any of this.
 *
 * <h2>Clients compare {@code type}</h2>
 *
 * <p>{@code type} is the machine-readable identity of an error: the configured base URI with a
 * stable kebab-case slug appended. {@code status} is too coarse to branch on and {@code title} is
 * prose. A slug is therefore published contract, as binding as a REST path — which is why {@link
 * com.openelements.spring.base.problem.StandardProblemType} spells every one of them out by hand
 * instead of deriving them from Spring's {@code HttpStatus} names.
 *
 * <h2>Producing a problem</h2>
 *
 * <p>Throw {@link com.openelements.spring.base.problem.ProblemException} with a {@link
 * com.openelements.spring.base.problem.ProblemType}; no handler is needed:
 *
 * <pre>{@code
 * public enum CrmProblemType implements ProblemType { ... DUPLICATE_CONTACT ... }
 *
 * throw new ProblemException(
 *     CrmProblemType.DUPLICATE_CONTACT,
 *     "A contact with this e-mail address already exists",
 *     Map.of("existingContactId", existing.id()));
 * }</pre>
 *
 * <p>An exception annotated {@code @ResponseStatus}, and a {@code ResponseStatusException}, are
 * rendered as the problem for their status. An application {@code @ExceptionHandler} always wins;
 * inject {@link com.openelements.spring.base.problem.ProblemFactory} to build the body from it.
 *
 * <h2>Configuration</h2>
 *
 * <pre>{@code
 * openelements.problem-details.enabled=true
 * openelements.problem-details.type-base=https://open-elements.com/problems/spring-services/
 * openelements.problem-details.include-instance=true
 * }</pre>
 *
 * <p>{@code enabled=false} restores the behaviour from before this feature in full: the old JSON
 * {@code 401}, Spring Boot's {@code BasicErrorController}, and ALTCHA's {@code {code, message}}.
 *
 * <h2>What never reaches the client</h2>
 *
 * <p>The message of an unexpected exception, its class name and its stack trace; Jackson's parser
 * messages; rejected parameter and field values; and the query string, which {@code instance} always
 * omits. What connects a client's report to the log entry is the {@code traceId} — see {@link
 * com.openelements.spring.base.trace}.
 *
 * <p>A {@code detail} written by the application <em>is</em> sent verbatim, and so is an interpolated
 * Bean Validation message. A message template that uses {@code ${validatedValue}} will therefore echo
 * the rejected value; that is the developer's choice, not the library's.
 */
package com.openelements.spring.base.problem;
