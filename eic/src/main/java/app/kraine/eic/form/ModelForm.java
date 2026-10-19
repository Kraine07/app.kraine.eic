package app.kraine.eic.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * The 'Create Model' and 'Update Model' form. A model is identified by its manufacturer and
 * description, so when updating, the original pair says which model is being edited.
 */
@Getter
@Setter
public class ModelForm {

    @NotNull(message = "Select a manufacturer.")
    private Long manufacturerId;

    @NotBlank(message = "Model description cannot be blank.")
    @Size(max = 255, message = "Model description must be at most 255 characters.")
    private String description;

    /** Only used when updating. */
    private Long originalManufacturerId;

    /** Only used when updating. */
    private String originalDescription;
}
