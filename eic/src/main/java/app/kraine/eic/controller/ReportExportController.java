package app.kraine.eic.controller;


import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import app.kraine.eic.equipmentManagement.EquipmentRow;
import app.kraine.eic.exception.InvalidReportSelectionException;
import app.kraine.eic.reportManagement.EquipmentReportCsv;
import app.kraine.eic.reportManagement.ReportViews;
import app.kraine.eic.service.EquipmentReportService;

import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * UC-42: the 'Export' button of each report. A report's export lives at the report's own address plus
 * .csv, with the same choices, and goes through the same service methods. It therefore applies exactly the
 * same rules as the page: an Editor can only export what they could see.
 */
@Controller
@RequestMapping("/reports")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'EDITOR')")
@RequiredArgsConstructor
public class ReportExportController {

    private final EquipmentReportService reportService;

    private record Export(String fileName, List<EquipmentRow> rows) {
    }

    @GetMapping("/editor-equipment.csv")
    public void editorEquipmentCsv(@RequestParam Long editorId, Authentication authentication,
                                HttpServletResponse response) throws IOException {
        export(response, () -> {
            ReportViews.EditorEquipmentReport report = reportService.generateEditorReport(editorId, authentication.getName());
            return new Export(fileName("editor", report.editorName()), report.rows());
        });
    }

    @GetMapping("/property-equipment.csv")
    public void propertyEquipmentCsv(@RequestParam Long propertyId, Authentication authentication,
                                    HttpServletResponse response) throws IOException {
        export(response, () -> {
            ReportViews.PropertyEquipmentReport report = reportService.generatePropertyReport(propertyId, authentication.getName());
            return new Export(fileName("property", report.propertyName()), report.rows());
        });
    }

    @GetMapping("/region-equipment.csv")
    public void regionEquipmentCsv(@RequestParam Long regionId, Authentication authentication,
                                HttpServletResponse response) throws IOException {
        export(response, () -> {
            ReportViews.RegionEquipmentReport report = reportService.generateRegionReport(regionId, authentication.getName());
            return new Export(fileName("region", report.regionName()), report.rows());
        });
    }

    @GetMapping("/all-equipment.csv")
    public void allEquipmentCsv(Authentication authentication, HttpServletResponse response) throws IOException {
        export(response, () -> {
            ReportViews.AllEquipmentReport report = reportService.generateAllEquipmentReport(authentication.getName());
            return new Export(fileName("all", null), report.rows());
        });
    }

    // ------------------------------------------------------------------ helpers

    /** Steps 2-3: generates the file and sends it as a download. A choice that cannot be used is a 400. */
    private void export(HttpServletResponse response, Supplier<Export> source) throws IOException {
        Export export;
        try {
            export = source.get();
        } catch (InvalidReportSelectionException ex) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
            return;
        }
        response.setContentType("text/csv");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + export.fileName() + "\"");
        response.setHeader("Cache-Control", "no-store"); // inventory data: never kept in a shared cache
        EquipmentReportCsv.write(response.getWriter(), export.rows());
    }

    /** For example equipment-editor-ana-silva-2026-10-24.csv. Only letters, digits and hyphens, so it is safe in a header. */
    public static String fileName(String kind, String subject) {
        return "equipment-" + kind + (subject == null ? "" : "-" + slug(subject))
                + "-" + LocalDate.now(ZoneOffset.UTC) + ".csv";
    }

    public static String slug(String text) {
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > 40) {
            slug = slug.substring(0, 40).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "report" : slug;
    }
}
