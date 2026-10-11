package app.kraine.eic.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;



/**
 * UC-01 step 5: what the login form must contain before any authentication is attempted.
 * No @Data/toString, so the password can never end up in a log line.
 */
@Getter
@Setter

public class LoginForm {

    @NotBlank(message = "Email is required.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 255, message = "Email must be at most 255 characters.")
    private String email;

    @NotBlank(message = "Password is required.")
    private String password;
}
