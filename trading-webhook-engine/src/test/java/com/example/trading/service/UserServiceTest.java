package com.example.trading.service;

import com.example.trading.dto.AuthResponse;
import com.example.trading.dto.SignInRequest;
import com.example.trading.dto.SignUpRequest;
import com.example.trading.entity.UserEntity;
import com.example.trading.exception.DuplicateEmailException;
import com.example.trading.exception.InvalidCredentialsException;
import com.example.trading.repository.UserRepository;
import com.example.trading.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService = new JwtService("test-only-jwt-signing-secret-must-be-at-least-32-bytes-long", 1440);
    private UserService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new UserService(userRepository, passwordEncoder, jwtService);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity entity = inv.getArgument(0);
            entity.setId(1L);
            return entity;
        });
    }

    private SignUpRequest signUpRequest(String name, String email, String password, String confirmPassword) {
        SignUpRequest request = new SignUpRequest();
        request.setName(name);
        request.setEmail(email);
        request.setPassword(password);
        request.setConfirmPassword(confirmPassword);
        return request;
    }

    @Test
    void signUp_success_hashesPasswordAndNeverStoresPlaintext() {
        when(userRepository.existsByEmail("usera@example.com")).thenReturn(false);

        UserEntity saved = service.signUp(signUpRequest("User A", "UserA@Example.com", "correct-password", "correct-password"));

        assertThat(saved.getEmail()).isEqualTo("usera@example.com"); // normalized
        assertThat(saved.getPasswordHash()).isNotEqualTo("correct-password");
        assertThat(passwordEncoder.matches("correct-password", saved.getPasswordHash())).isTrue();
    }

    @Test
    void signUp_duplicateEmail_throws() {
        when(userRepository.existsByEmail("usera@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.signUp(signUpRequest("User A", "usera@example.com", "correct-password", "correct-password")))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void signIn_correctPassword_returnsTokenAndProfile() {
        UserEntity existing = UserEntity.create("User A", "usera@example.com", passwordEncoder.encode("correct-password"));
        existing.setId(7L);
        when(userRepository.findByEmail("usera@example.com")).thenReturn(Optional.of(existing));

        SignInRequest request = new SignInRequest();
        request.setEmail("UserA@example.com");
        request.setPassword("correct-password");

        AuthResponse response = service.signIn(request);

        assertThat(response.getId()).isEqualTo(7L);
        assertThat(response.getToken()).isNotBlank();
        assertThat(jwtService.parseToken(response.getToken())).isPresent();
    }

    @Test
    void signIn_wrongPassword_throwsGenericInvalidCredentials() {
        UserEntity existing = UserEntity.create("User A", "usera@example.com", passwordEncoder.encode("correct-password"));
        when(userRepository.findByEmail("usera@example.com")).thenReturn(Optional.of(existing));

        SignInRequest request = new SignInRequest();
        request.setEmail("usera@example.com");
        request.setPassword("wrong-password");

        assertThatThrownBy(() -> service.signIn(request)).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void signIn_unknownEmail_throwsTheSameGenericException() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        SignInRequest request = new SignInRequest();
        request.setEmail("nobody@example.com");
        request.setPassword("whatever");

        assertThatThrownBy(() -> service.signIn(request)).isInstanceOf(InvalidCredentialsException.class);
    }
}
