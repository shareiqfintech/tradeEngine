package com.example.trading.service;

import com.example.trading.dto.AuthResponse;
import com.example.trading.dto.SignInRequest;
import com.example.trading.dto.SignUpRequest;
import com.example.trading.entity.UserEntity;
import com.example.trading.exception.DuplicateEmailException;
import com.example.trading.exception.InvalidCredentialsException;
import com.example.trading.repository.UserRepository;
import com.example.trading.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Sign-up/sign-in business logic. Passwords are never logged or stored in
 * plaintext (BCrypt via {@link PasswordEncoder}); a failed sign-in never
 * reveals whether the email exists (unknown email and wrong password both
 * raise the same {@link InvalidCredentialsException}).
 */
@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public UserEntity signUp(SignUpRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("An account with this email already exists");
        }
        UserEntity entity = UserEntity.create(request.getName().trim(), email, passwordEncoder.encode(request.getPassword()));
        UserEntity saved = userRepository.save(entity);
        log.info("USER_REGISTERED userId={}", saved.getId());
        return saved;
    }

    public AuthResponse signIn(SignInRequest request) {
        String email = normalizeEmail(request.getEmail());
        UserEntity user = userRepository.findByEmail(email)
                .filter(u -> passwordEncoder.matches(request.getPassword(), u.getPasswordHash()))
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        String token = jwtService.issueToken(user.getId(), user.getEmail(), user.getName());
        log.info("USER_SIGNED_IN userId={}", user.getId());
        return AuthResponse.builder().id(user.getId()).name(user.getName()).email(user.getEmail()).token(token).build();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
