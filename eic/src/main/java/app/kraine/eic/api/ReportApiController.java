package app.kraine.eic.api;

import app.kraine.eic.controller.ReportExportController;
import app.kraine.eic.equipmentManagement.EquipmentRow;
import app.kraine.eic.reportManagement.EquipmentReportCsv;
import app.kraine.eic.reportManagement.ReportViews.AllEquipmentReport;
import app.kraine.eic.reportManagement.ReportViews.EditorEquipmentReport;
import app.kraine.eic.reportManagement.ReportViews.Option;
import app.kraine.eic.reportManagement.ReportViews.PropertyEquipmentReport;
import app.kraine.eic.reportManagement.ReportViews.RegionEquipmentReport;
import app.kraine.eic.service.EquipmentReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JSON version of the four reports (RG-1 to RG-4, UC-38 to UC-41) and their CSV export (UC-42), for the Flutter
 * app. Administrators and Editors. Every call goes through the same EquipmentReportService as the web pages, so
 * the same rule applies: an Administrator can report on anything, an Editor only on themselves, their own
 * properties, and the part of a region that is theirs (anything else is a 403).
 *
 * <p>The CSV is built by the same writer as the web export, so formulas are defused and the columns are the same.
 */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'EDITOR')")
@RequiredArgsConstructor
public class ReportApiController {

    private final EquipmentReportService reportService;

    /** One line of a report. [manufactured] is a month such as 2026-10, or null. */
    public record ReportItem(Long id, String serialNumber, String manufactured, String manufacturerName,
                             String modelDescription, String propertyName, String locationName) {
    }

    public record OptionsResponse(List<Option> options) {
    }

    /** [notes] are extra lines to show under the title, such as the region of a property or that the list is limited. */
    public record ReportResponse(String title, List<String> notes, String generatedAt, List<ReportItem> items) {
    }

    // ------------------------------------------------------------------ choices

    @GetMapping("/options/editors")
    public OptionsResponse editors(Authentication authentication) {
        return new OptionsResponse(reportService.editorOptions(authentication.getName()));
    }

    @GetMapping("/options/properties")
    public OptionsResponse properties(Authentication authentication) {
        return new OptionsResponse(reportService.propertyOptions(authentication.getName()));
    }

    @GetMapping("/options/regions")
    public OptionsResponse regions(Authentication authentication) {
        return new OptionsResponse(reportService.regionOptions(authentication.getName()));
    }

    // ------------------------------------------------------------------ the reports

    /** UC-38. */
    @GetMapping("/editor-equipment")
    public ReportResponse editorEquipment(@RequestParam Long editorId, Authentication authentication) {
        EditorEquipmentReport report = reportService.generateEditorReport(editorId, authentication.getName());
        return response("Equipment assigned to " + report.editorName(), List.of(), report.generatedAt(), report.rows());
    }

    /** UC-39. */
    @GetMapping("/property-equipment")
    public ReportResponse propertyEquipment(@RequestParam Long propertyId, Authentication authentication) {
        PropertyEquipmentReport report = reportService.generatePropertyReport(propertyId, authentication.getName());
        return response("Equipment at " + report.propertyName(),
                List.of("Region: " + report.regionName(), "Editor: " + report.editorName()),
                report.generatedAt(), report.rows());
    }

    /** UC-40. */
    @GetMapping("/region-equipment")
    public ReportResponse regionEquipment(@RequestParam Long regionId, Authentication authentication) {
        RegionEquipmentReport report = reportService.generateRegionReport(regionId, authentication.getName());
        return response("Equipment in " + report.regionName(),
                report.restricted() ? List.of("Showing only the equipment at your properties in this region.") : List.of(),
                report.generatedAt(), report.rows());
    }

    /** UC-41. */
    @GetMapping("/all-equipment")
    public ReportResponse allEquipment(Authentication authentication) {
        AllEquipmentReport report = reportService.generateAllEquipmentReport(authentication.getName());
        return response("All equipment",
                report.restricted() ? List.of("Showing only the equipment at your properties.") : List.of(),
                report.generatedAt(), report.rows());
    }

    // ------------------------------------------------------------------ the CSV export (UC-42)

    @GetMapping("/editor-equipment.csv")
    public ResponseEntity<byte[]> editorEquipmentCsv(@RequestParam Long editorId, Authentication authentication) throws IOException {
        EditorEquipmentReport report = reportService.generateEditorReport(editorId, authentication.getName());
        return csv(ReportExportController.fileName("editor", report.editorName()), report.rows());
    }

    @GetMapping("/property-equipment.csv")
    public ResponseEntity<byte[]> propertyEquipmentCsv(@RequestParam Long propertyId, Authentication authentication) throws IOException {
        PropertyEquipmentReport report = reportService.generatePropertyReport(propertyId, authentication.getName());
        return csv(ReportExportController.fileName("property", report.propertyName()), report.rows());
    }

    @GetMapping("/region-equipment.csv")
    public ResponseEntity<byte[]> regionEquipmentCsv(@RequestParam Long regionId, Authentication authentication) throws IOException {
        RegionEquipmentReport report = reportService.generateRegionReport(regionId, authentication.getName());
        return csv(ReportExportController.fileName("region", report.regionName()), report.rows());
    }

    @GetMapping("/all-equipment.csv")
    public ResponseEntity<byte[]> allEquipmentCsv(Authentication authentication) throws IOException {
        AllEquipmentReport report = reportService.generateAllEquipmentReport(authentication.getName());
        return csv(ReportExportController.fileName("all", null), report.rows());
    }

    // ------------------------------------------------------------------ helpers

    private static ReportResponse response(String title, List<String> notes, String generatedAt, List<EquipmentRow> rows) {
        List<ReportItem> items = rows.stream()
                .map(row -> new ReportItem(row.id(), row.serialNumber(),
                        row.manufacturedDate() == null ? null : row.manufacturedDate().toString(),
                        row.manufacturerName(), row.modelDescription(), row.propertyName(), row.locationName()))
                .toList();
        return new ReportResponse(title, notes, generatedAt, items);
    }

    private static ResponseEntity<byte[]> csv(String fileName, List<EquipmentRow> rows) throws IOException {
        StringWriter writer = new StringWriter();
        EquipmentReportCsv.write(writer, rows);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store") // inventory data: never kept in a shared cache
                .body(writer.toString().getBytes(StandardCharsets.UTF_8));
    }
}
