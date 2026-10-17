package app.kraine.eic.locationManagement;

import app.kraine.eic.form.LocationForm;

/** Read-only data for the location pages. Templates call the accessors, e.g. l.name(). */
public final class LocationViews {

    private LocationViews() {
    }

    public record LocationRow(Long propertyId, String propertyName, String name) {
    }

    public record LocationEditData(Long propertyId, String propertyName, LocationForm form) {
    }
}
