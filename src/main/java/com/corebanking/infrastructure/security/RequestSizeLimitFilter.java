package com.corebanking.infrastructure.security;

import com.corebanking.domain.exception.ErrorCode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class RequestSizeLimitFilter extends OncePerRequestFilter {

    static final long MAX_BODY_BYTES = 16 * 1024;

    private final ObjectMapper mapper;

    public RequestSizeLimitFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long length = request.getContentLengthLong();
        boolean chunked = length < 0 && request.getHeader("Transfer-Encoding") != null;
        if (length > MAX_BODY_BYTES || chunked) {
            ProblemResponses.write(response, mapper, HttpStatus.CONTENT_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE);
            return;
        }
        chain.doFilter(request, response);
    }
}
