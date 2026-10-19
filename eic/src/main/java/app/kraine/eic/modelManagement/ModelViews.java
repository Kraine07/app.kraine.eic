package app.kraine.eic.modelManagement;

import app.kraine.eic.form.ModelForm;

/** Read-only data for the model pages. Templates call the accessors, e.g. m.description(). */
public final class ModelViews {

    private ModelViews() {
    }

    public record ModelRow(Long manufacturerId, String manufacturerName, String description) {
    }

    public record ModelEditData(ModelForm form) {
    }

    /** One entry of the manufacturer drop-down. */
    public record Option(Long id, String label) {
    }
}
