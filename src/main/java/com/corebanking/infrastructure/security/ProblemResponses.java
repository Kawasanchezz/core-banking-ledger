package com.corebanking.infrastructure.security;

import com.corebanking.domain.exception.ErrorCode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import java.io.IOException;

final class ProblemResponses {

    private ProblemResponses() {
    }

    static void write(HttpServletResponse res, ObjectMapper mapper, HttpStatus status, ErrorCode code)
            throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, code.title());
        pd.setTitle(code.title());
        pd.setProperty("code", code.name());
        pd.setProperty("correlationId", MDC.get("correlationId"));
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), pd);
    }
}
