package app.kraine.eic.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * The 'Create Location' and 'Update Location' form. A location is identified by its property and
 * name, so when updating, [originalName] says which location is being edited.
 */
@Getter
@Setter
public class LocationForm {

    @NotNull(message = "Select a property.")
    private Long propertyId;

    @NotBlank(message = "Location name cannot be blank.")
    @Size(max = 255, message = "Location name must be at most 255 characters.")
    private String name;

    /** Only used when updating. */
    private String originalName;
}
