package com.devmate.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.user.dto.AuthResponse;
import com.devmate.user.dto.LoginRequest;
import com.devmate.user.dto.RegisterRequest;
import com.devmate.user.dto.UserResponse;
import com.devmate.user.entity.AppUser;
import com.devmate.user.mapper.AppUserMapper;
import com.devmate.user.security.AuthenticatedUser;
import com.devmate.user.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class AuthService {

    private static final String ACTIVE = "ACTIVE";

    private final AppUserMapper appUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;

    public AuthService(
            AppUserMapper appUserMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            CurrentUserService currentUserService
    ) {
        this.appUserMapper = appUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = normalizeEmail(request.email());
        validateBcryptPassword(request.password());
        rejectDuplicates(username, email);

        LocalDateTime now = LocalDateTime.now();
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEmail(email);
        user.setStatus(ACTIVE);
        user.setDeleted(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            if (appUserMapper.insert(user) != 1) {
                throw new IllegalStateException("用户注册失败");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名或邮箱已存在");
        }
        return createAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        AppUser user = appUserMapper.selectOne(Wrappers.lambdaQuery(AppUser.class)
                .eq(AppUser::getUsername, request.username().trim()));
        if (user == null || !ACTIVE.equals(user.getStatus())
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        return createAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        AppUser user = appUserMapper.selectById(currentUserService.getRequiredUser().id());
        if (user == null || !ACTIVE.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return UserResponse.from(user);
    }

    private AuthResponse createAuthResponse(AppUser user) {
        JwtService.IssuedToken token = jwtService.issue(new AuthenticatedUser(user.getId(), user.getUsername()));
        return new AuthResponse(token.value(), "Bearer", token.expiresAt(), UserResponse.from(user));
    }

    private void rejectDuplicates(String username, String email) {
        Long count = appUserMapper.selectCount(Wrappers.lambdaQuery(AppUser.class)
                .and(query -> {
                    query.eq(AppUser::getUsername, username);
                    if (email != null) {
                        query.or().eq(AppUser::getEmail, email);
                    }
                }));
        if (count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名或邮箱已存在");
        }
    }

    private void validateBcryptPassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "密码UTF-8编码后不能超过72字节");
        }
    }

    private String normalizeEmail(String email) {
        return StringUtils.hasText(email) ? email.trim().toLowerCase(Locale.ROOT) : null;
    }
}
