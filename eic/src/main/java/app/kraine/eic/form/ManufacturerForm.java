package app.kraine.eic.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** The 'Create Manufacturer' and 'Update Manufacturer' form. */
@Getter
@Setter
public class ManufacturerForm {

    @NotBlank(message = "Manufacturer name cannot be blank.")
    @Size(max = 255, message = "Manufacturer name must be at most 255 characters.")
    private String name;
}
