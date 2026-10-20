package app.kraine.eic.equipmentManagement;


import java.time.YearMonth;
import java.util.List;

import app.kraine.eic.form.EquipmentForm;
 
/** Read-only data for the equipment pages. Templates call the accessors, e.g. e.serialNumber(). */
public final class EquipmentViews {
 
    private EquipmentViews() {
    }
 
    public record EquipmentPage(List<EquipmentRow> rows, int page, int totalPages, long totalElements) {
 
        public boolean hasPrevious() {
            return page > 0;
        }
 
        public boolean hasNext() {
            return page + 1 < totalPages;
        }
 
        public int displayPage() {
            return page + 1;
        }
    }
 
    /** US-17: everything shown on the equipment details page. [imageName] is null when there is no image. */
    public record EquipmentDetail(Long id, String serialNumber, YearMonth manufacturedDate, String manufacturerName,
                                String modelDescription, String propertyName, String locationName,
                                String editorName, String imageName) {
    }
 
    public record EquipmentEditData(Long id, EquipmentForm form) {
    }
 
    /** One entry of the model or location drop-down; [key] comes from CompositeKeys. */
    public record Option(String key, String label) {
    }
}