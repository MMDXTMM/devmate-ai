package com.devmate.user.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.devmate.user.config.SecurityProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;

@Service
public class JwtService {

    private static final String ISSUER = "devmate-ai";

    private final SecurityProperties properties;
    private final Clock clock;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    @Autowired
    public JwtService(SecurityProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtService(SecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        if (properties.isEnabled() && (!StringUtils.hasText(properties.getJwtSecret())
                || properties.getJwtSecret().length() < 32)) {
            throw new IllegalStateException("启用安全配置时 DEVMATE_JWT_SECRET 至少需要32个字符");
        }
        String secret = StringUtils.hasText(properties.getJwtSecret())
                ? properties.getJwtSecret()
                : "test-security-disabled-secret-32-characters";
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm).withIssuer(ISSUER).build();
    }

    public IssuedToken issue(AuthenticatedUser user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.getAccessTokenTtl());
        String token = JWT.create()
                .withIssuer(ISSUER)
                .withSubject(user.id().toString())
                .withClaim("username", user.username())
                .withIssuedAt(issuedAt)
                .withExpiresAt(expiresAt)
                .sign(algorithm);
        return new IssuedToken(token, expiresAt);
    }

    public AuthenticatedUser verify(String token) {
        try {
            DecodedJWT jwt = verifier.verify(token);
            return new AuthenticatedUser(
                    Long.valueOf(jwt.getSubject()),
                    jwt.getClaim("username").asString()
            );
        } catch (JWTVerificationException | NumberFormatException exception) {
            throw new InvalidJwtException(exception);
        }
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }

    public static final class InvalidJwtException extends RuntimeException {
        InvalidJwtException(Throwable cause) {
            super(cause);
        }
    }
}
