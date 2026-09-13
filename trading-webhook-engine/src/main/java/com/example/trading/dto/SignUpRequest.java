package com.example.trading.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignUpRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;

    /** Bean-validated cross-field check - flows through the existing MethodArgumentNotValidException/fieldErrors handling with no new error-handling code. */
    @AssertTrue(message = "Passwords do not match")
    @JsonIgnore
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(confirmPassword);
    }
}
