package app.kraine.eic.propertyManagement;

import app.kraine.eic.form.PropertyForm;

/** Read-only data for the property pages. Templates call the accessors, e.g. p.name(). */
public final class PropertyViews {

    private PropertyViews() {
    }

    public record PropertyRow(Long id, String name, String regionName, String editorName, int locationCount) {
    }

    public record PropertyEditData(Long id, PropertyForm form, int locationCount) {
    }

    /** One entry of a drop-down list. */
    public record Option(Long id, String label) {
    }
}
