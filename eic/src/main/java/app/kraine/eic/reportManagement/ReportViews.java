package app.kraine.eic.reportManagement;




import java.util.List;

import app.kraine.eic.equipmentManagement.EquipmentRow;
 
/** Read-only data for the report pages. Templates call the accessors, e.g. report.rows(). */
public final class ReportViews {
 
    private ReportViews() {
    }
 
    /** One entry of a drop-down list. */
    public record Option(Long id, String label) {
    }
 
    /** UC-41: every item the user may see. [restricted] means an Editor sees only the items at their own properties. */
    public record AllEquipmentReport(List<EquipmentRow> rows, String generatedAt, boolean restricted) {
    }
 
    /** UC-40: the equipment at the properties of one region. [restricted] means an Editor sees only their own part. */
    public record RegionEquipmentReport(String regionName, List<EquipmentRow> rows, String generatedAt,
                                        boolean restricted) {
    }
 
    /** UC-39: the equipment at the locations of one property. */
    public record PropertyEquipmentReport(String propertyName, String regionName, String editorName,
                                        List<EquipmentRow> rows, String generatedAt) {
    }
 
    /** UC-38: the equipment at the properties of one editor. [generatedAt] is shown in UTC. */
    public record EditorEquipmentReport(String editorName, String editorEmail, List<EquipmentRow> rows,
                                        String generatedAt) {
    }
}