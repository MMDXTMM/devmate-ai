package com.devmate.user.config;

import com.devmate.user.security.ApiAccessDeniedHandler;
import com.devmate.user.security.ApiAuthenticationEntryPoint;
import com.devmate.user.security.JwtAuthenticationFilter;
import com.devmate.user.security.JwtService;
import com.devmate.user.security.ProjectAuthorizationFilter;
import com.devmate.user.service.ProjectAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityProperties properties,
            JwtService jwtService,
            ProjectAccessService projectAccessService,
            ObjectMapper objectMapper
    ) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());

        if (!properties.isEnabled()) {
            http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
            return http.build();
        }

        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService, objectMapper);
        ProjectAuthorizationFilter projectFilter = new ProjectAuthorizationFilter(
                projectAccessService,
                objectMapper
        );
        http.exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new ApiAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new ApiAccessDeniedHandler(objectMapper)))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/health",
                                "/actuator/health"
                        ).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(projectFilter, JwtAuthenticationFilter.class);
        return http.build();
    }
}
