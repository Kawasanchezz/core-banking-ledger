package com.corebanking.api.exception;

import com.corebanking.domain.exception.BusinessException;
import com.corebanking.domain.exception.ErrorCode;
import com.corebanking.infrastructure.logging.CorrelationIdFilter;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<Object> handleBusiness(BusinessException ex) {
        log.warn("Regra de negócio violada: {} - {}", ex.getCode(), ex.getMessage());
        return problem(ex.getCode(), ex.getMessage(), null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraint(ConstraintViolationException ex) {
        return problem(ErrorCode.VALIDATION_ERROR, "Parâmetros inválidos", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violação de integridade: {}", ex.getMostSpecificCause().getClass().getSimpleName());
        return problem(ErrorCode.CONCURRENT_UPDATE, "Conflito ao gravar os dados. Tente novamente.", null);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ResponseEntity<Object> handleLocking(Exception ex) {
        log.warn("Falha de lock/concorrência: {}", ex.getClass().getSimpleName());
        return problem(ErrorCode.CONCURRENT_UPDATE,
                "A conta está sendo alterada por outra operação. Tente novamente.", null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Erro inesperado", ex);
        return problem(ErrorCode.INTERNAL_ERROR, "Erro interno. Informe o correlationId ao suporte.", null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return problem(ErrorCode.VALIDATION_ERROR, "Dados inválidos", errors);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail pd) {
            if (pd.getProperties() == null || !pd.getProperties().containsKey("code")) {
                ErrorCode code = codeFor(ex, statusCode);
                pd.setProperty("code", code.name());
            }
            pd.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        }
        return response;
    }

    private static ErrorCode codeFor(Exception ex, HttpStatusCode status) {
        if (ex instanceof MissingRequestHeaderException h && "Idempotency-Key".equalsIgnoreCase(h.getHeaderName())) {
            return ErrorCode.IDEMPOTENCY_KEY_MISSING;
        }
        return status.value() == HttpStatus.BAD_REQUEST.value() ? ErrorCode.VALIDATION_ERROR : ErrorCode.INTERNAL_ERROR;
    }

    private static ResponseEntity<Object> problem(ErrorCode code, String detail, @Nullable Map<String, String> errors) {
        HttpStatus status = HttpStatus.valueOf(code.httpStatus());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(code.title());
        pd.setProperty("code", code.name());
        pd.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        if (errors != null) {
            pd.setProperty("errors", errors);
        }
        return ResponseEntity.status(status).body(pd);
    }
}
