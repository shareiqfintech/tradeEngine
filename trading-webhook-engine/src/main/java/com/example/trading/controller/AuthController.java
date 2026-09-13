package com.example.trading.controller;

import com.example.trading.dto.AuthResponse;
import com.example.trading.dto.CurrentUserResponse;
import com.example.trading.dto.SignInRequest;
import com.example.trading.dto.SignUpRequest;
import com.example.trading.security.AuthenticatedUser;
import com.example.trading.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Sign up / sign in / current user / logout. See {@link com.example.trading.security.SecurityConfig} for which of these require an authenticated request. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/signup")
    public ResponseEntity<Void> signUp(@Valid @RequestBody SignUpRequest request) {
        userService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody SignInRequest request) {
        return userService.signIn(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return CurrentUserResponse.builder().id(user.id()).name(user.name()).email(user.email()).build();
    }

    /** Stateless JWT: there is no server-side session to invalidate - the client discards its token. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok().build();
    }
}
