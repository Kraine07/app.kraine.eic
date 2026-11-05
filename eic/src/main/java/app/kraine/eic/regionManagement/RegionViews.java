package app.kraine.eic.regionManagement;

import app.kraine.eic.form.RegionForm;

/** Read-only data for the region pages. Templates call the accessors, e.g. r.name(). */
public final class RegionViews {

    private RegionViews() {
    }

    public record RegionRow(Long id, String name) {
    }

    public record RegionEditData(Long id, RegionForm form) {
    }
}
