package app.kraine.eic.form;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** The 'Create Region' and 'Update Region' form. */
@Getter
@Setter
public class RegionForm {

    @NotBlank(message = "Region name cannot be blank.")
    @Size(max = 255, message = "Region name must be at most 255 characters.")
    private String name;
}