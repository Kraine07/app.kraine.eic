package app.kraine.eic.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordForm {

    @NotBlank(message = "Current password is required.")
    private String currentPassword;


    @NotBlank(message = "New password is required.")
    private String newPassword;


    @NotBlank(message = "Confirm the password.")
    private String confirmPassword;
}

