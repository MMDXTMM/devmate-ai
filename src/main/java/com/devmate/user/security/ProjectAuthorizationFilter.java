package com.devmate.user.security;

import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.user.service.ProjectAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProjectAuthorizationFilter extends OncePerRequestFilter {

    private static final Pattern PROJECT_PATH = Pattern.compile("^/api/projects/([0-9]+)(?:/.*)?$");

    private final ProjectAccessService projectAccessService;
    private final ObjectMapper objectMapper;

    public ProjectAuthorizationFilter(
            ProjectAccessService projectAccessService,
            ObjectMapper objectMapper
    ) {
        this.projectAccessService = projectAccessService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Matcher matcher = PROJECT_PATH.matcher(request.getRequestURI());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!matcher.matches() || authentication == null
                || !(authentication.getPrincipal() instanceof AuthenticatedUser)) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            projectAccessService.requireMember(Long.valueOf(matcher.group(1)));
            filterChain.doFilter(request, response);
        } catch (BusinessException exception) {
            ErrorCode errorCode = exception.getErrorCode() == ErrorCode.UNAUTHORIZED
                    ? ErrorCode.UNAUTHORIZED
                    : ErrorCode.FORBIDDEN;
            int status = errorCode == ErrorCode.UNAUTHORIZED ? 401 : 403;
            ApiSecurityResponseWriter.write(response, objectMapper, status, errorCode);
        }
    }
}
