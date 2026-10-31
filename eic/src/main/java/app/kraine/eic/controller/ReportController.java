package app.kraine.eic.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import app.kraine.eic.exception.InvalidReportSelectionException;
import app.kraine.eic.reportManagement.ReportViews.Option;
import app.kraine.eic.service.EquipmentReportService;

import java.util.List;

/**
 * The 'Reports' page and the reports on it (RG-1 to RG-4). Each report is a GET with its choices in the
 * address, so a generated report can be bookmarked and, later, exported from the same address.
 */
@Controller
@RequestMapping("/reports")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'EDITOR')")
@RequiredArgsConstructor
public class ReportController {

    static final String INDEX_VIEW = "reports/index";
    static final String EDITOR_EQUIPMENT_VIEW = "reports/editor-equipment";
    static final String PROPERTY_EQUIPMENT_VIEW = "reports/property-equipment";
    static final String REGION_EQUIPMENT_VIEW = "reports/region-equipment";
    static final String ALL_EQUIPMENT_VIEW = "reports/all-equipment";

    private final EquipmentReportService reportService;

    /** Steps 1-2 of UC-38: the page where a report is chosen. */
    @GetMapping
    public String index() {
        return INDEX_VIEW;
    }

    /**
     * UC-38. Without a choice the form is shown. 'Generate' (or a link that carries editorId) produces the
     * report. An Editor only has themselves to choose, so that choice is already selected.
     */
    @GetMapping("/editor-equipment")
    public String editorEquipment(@RequestParam(required = false) Long editorId,
                                @RequestParam(required = false) String generate,
                                Model model,
                                Authentication authentication) {
        List<Option> editors = reportService.editorOptions(authentication.getName());
        model.addAttribute("editors", editors);
        model.addAttribute("selectedEditorId", editorId == null && editors.size() == 1 ? editors.get(0).id() : editorId);

        boolean generating = generate != null || editorId != null;
        if (!generating) {
            return EDITOR_EQUIPMENT_VIEW;
        }
        if (editorId == null) {
            model.addAttribute("errorMessage", "Select an editor.");
            return EDITOR_EQUIPMENT_VIEW;
        }
        try {
            model.addAttribute("report", reportService.generateEditorReport(editorId, authentication.getName()));
        } catch (InvalidReportSelectionException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
        }
        return EDITOR_EQUIPMENT_VIEW;
    }

    /**
     * UC-39. The same shape as the editor report: the form first, the report on 'Generate' or when the address
     * carries propertyId. An Editor who has only one property sees it already selected.
     */
    @GetMapping("/property-equipment")
    public String propertyEquipment(@RequestParam(required = false) Long propertyId,
                                    @RequestParam(required = false) String generate,
                                    Model model,
                                    Authentication authentication) {
        List<Option> properties = reportService.propertyOptions(authentication.getName());
        model.addAttribute("properties", properties);
        model.addAttribute("selectedPropertyId",
                propertyId == null && properties.size() == 1 ? properties.get(0).id() : propertyId);

        boolean generating = generate != null || propertyId != null;
        if (!generating) {
            return PROPERTY_EQUIPMENT_VIEW;
        }
        if (propertyId == null) {
            model.addAttribute("errorMessage", "Select a property.");
            return PROPERTY_EQUIPMENT_VIEW;
        }
        try {
            model.addAttribute("report", reportService.generatePropertyReport(propertyId, authentication.getName()));
        } catch (InvalidReportSelectionException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
        }
        return PROPERTY_EQUIPMENT_VIEW;
    }

    /**
     * UC-40. The form first, the report on 'Generate' or when the address carries regionId. An Editor who is in
     * only one region sees it already selected.
     */
    @GetMapping("/region-equipment")
    public String regionEquipment(@RequestParam(required = false) Long regionId,
                                @RequestParam(required = false) String generate,
                                Model model,
                                Authentication authentication) {
        List<Option> regions = reportService.regionOptions(authentication.getName());
        model.addAttribute("regions", regions);
        model.addAttribute("selectedRegionId", regionId == null && regions.size() == 1 ? regions.get(0).id() : regionId);

        boolean generating = generate != null || regionId != null;
        if (!generating) {
            return REGION_EQUIPMENT_VIEW;
        }
        if (regionId == null) {
            model.addAttribute("errorMessage", "Select a region.");
            return REGION_EQUIPMENT_VIEW;
        }
        try {
            model.addAttribute("report", reportService.generateRegionReport(regionId, authentication.getName()));
        } catch (InvalidReportSelectionException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
        }
        return REGION_EQUIPMENT_VIEW;
    }

    /** UC-41. There is nothing to choose: the page shows 'Generate', and 'Generate' shows the report. */
    @GetMapping("/all-equipment")
    public String allEquipment(@RequestParam(required = false) String generate,
                            Model model,
                            Authentication authentication) {
        if (generate != null) {
            model.addAttribute("report", reportService.generateAllEquipmentReport(authentication.getName()));
        }
        return ALL_EQUIPMENT_VIEW;
    }
}