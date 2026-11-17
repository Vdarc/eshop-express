package com.traslation.backend.common.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

/** Makes JSON request data available to the audit interceptor after MVC has consumed it. */
public final class OperationAuditRequestFilter extends OncePerRequestFilter implements Ordered {
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpServletRequest wrapped =
                request instanceof ContentCachingRequestWrapper
                        ? request
                        : new ContentCachingRequestWrapper(request, 64 * 1024);
        chain.doFilter(wrapped, response);
    }
}
