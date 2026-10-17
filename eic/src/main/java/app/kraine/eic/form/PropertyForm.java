package app.kraine.eic.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** The 'Create Property' and 'Update Property' form (PM-2, PM-3). */
@Getter
@Setter
public class PropertyForm {

    @NotBlank(message = "Property name cannot be blank.")
    @Size(max = 255, message = "Property name must be at most 255 characters.")
    private String name;

    @NotNull(message = "Select a region.")
    private Long regionId;

    /** Optional: a property can be created first and given an editor later (UA-5). */
    private Long editorId;
}
