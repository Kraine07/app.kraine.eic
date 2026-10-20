package app.kraine.eic.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
 
/** The 'Create Equipment' and 'Update Equipment' form (UC-23, UC-24). */
@Getter
@Setter
public class EquipmentForm {
 
    /** Optional. A blank serial number is stored as null, so any number of items can have none. */
    @Size(max = 255, message = "Serial number must be at most 255 characters.")
    private String serialNumber;
 
    /** Optional, as a month such as 2026-10 (what an HTML month input sends). */
    private String manufacturedDate;
 
    /** A model, as packed by {@link CompositeKeys#encode}. */
    @NotBlank(message = "Select a model.")
    private String modelKey;
 
    /** A location, as packed by {@link CompositeKeys#encode}. */
    @NotBlank(message = "Select a location.")
    private String locationKey;
}
 