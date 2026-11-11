package com.devmate.user.security;

import com.devmate.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final SecurityContextRepository contextRepository;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper,
                                   SecurityContextRepository contextRepository) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.contextRepository = contextRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            AuthenticatedUser principal = jwtService.verify(token);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            // Retain the verified identity only for this request's ASYNC/ERROR redispatches.
            // No HTTP session or anonymous dispatcher bypass is introduced.
            contextRepository.saveContext(context, request, response);
            filterChain.doFilter(request, response);
        } catch (JwtService.InvalidJwtException exception) {
            SecurityContextHolder.clearContext();
            ApiSecurityResponseWriter.write(response, objectMapper, 401, ErrorCode.UNAUTHORIZED);
        }
    }
}
