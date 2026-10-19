package app.kraine.eic.manufacturerManagement;

import app.kraine.eic.form.ManufacturerForm;

/** Read-only data for the manufacturer pages. Templates call the accessors, e.g. m.name(). */
public final class ManufacturerViews {

    private ManufacturerViews() {
    }

    public record ManufacturerRow(Long id, String name) {
    }

    public record ManufacturerEditData(Long id, ManufacturerForm form) {
    }
}
