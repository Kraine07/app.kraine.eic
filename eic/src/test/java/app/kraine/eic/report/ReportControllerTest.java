package app.kraine.eic.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import app.kraine.eic.controller.ReportController;
import app.kraine.eic.exception.InvalidReportSelectionException;
import app.kraine.eic.reportManagement.ReportViews.AllEquipmentReport;
import app.kraine.eic.reportManagement.ReportViews.EditorEquipmentReport;
import app.kraine.eic.reportManagement.ReportViews.Option;
import app.kraine.eic.reportManagement.ReportViews.PropertyEquipmentReport;
import app.kraine.eic.reportManagement.ReportViews.RegionEquipmentReport;
import app.kraine.eic.service.EquipmentReportService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    private static final String USER = "admin@example.com";

    @Mock private EquipmentReportService reportService;

    private final Model model = new ConcurrentModel();
    private final Authentication user = UsernamePasswordAuthenticationToken.authenticated(USER, null, List.of());

    private ReportController controller() {
        return new ReportController(reportService);
    }

    private static final List<Option> TWO_EDITORS =
            List.of(new Option(2L, "Ana Silva (ana@example.com)"), new Option(3L, "Eva Alves (eva@example.com)"));

    @Test
    void theReportsPageIsShown() {
        assertEquals("reports/index", controller().index());
    }

    @Test
    void theFormIsShownWithTheEditorsAndNoReportYet() {
        when(reportService.editorOptions(USER)).thenReturn(TWO_EDITORS);

        String view = controller().editorEquipment(null, null, model, user);

        assertEquals("reports/editor-equipment", view);
        assertEquals(TWO_EDITORS, model.getAttribute("editors"));
        assertNull(model.getAttribute("selectedEditorId"));
        assertNull(model.getAttribute("report"));
        assertNull(model.getAttribute("errorMessage"));
        verify(reportService, never()).generateEditorReport(any(), any());
    }

    @Test
    void anEditorWhoOnlyHasThemselvesToChooseSeesThemPreselected() {
        when(reportService.editorOptions(USER)).thenReturn(List.of(new Option(2L, "Ana Silva (ana@example.com)")));

        controller().editorEquipment(null, null, model, user);

        assertEquals(2L, model.getAttribute("selectedEditorId"));
        assertNull(model.getAttribute("report"));
    }

    @Test
    void generateShowsTheReportForTheSelectedEditor() {
        when(reportService.editorOptions(USER)).thenReturn(TWO_EDITORS);
        EditorEquipmentReport report = new EditorEquipmentReport("Ana Silva", "ana@example.com", List.of(), "2026-10-24 12:00:00 UTC");
        when(reportService.generateEditorReport(2L, USER)).thenReturn(report);

        controller().editorEquipment(2L, "true", model, user);

        assertSame(report, model.getAttribute("report"));
        assertEquals(2L, model.getAttribute("selectedEditorId"));
    }

    @Test
    void aLinkThatCarriesTheEditorAlsoGeneratesTheReport() {
        when(reportService.editorOptions(USER)).thenReturn(TWO_EDITORS);
        EditorEquipmentReport report = new EditorEquipmentReport("Ana Silva", "ana@example.com", List.of(), "now");
        when(reportService.generateEditorReport(2L, USER)).thenReturn(report);

        controller().editorEquipment(2L, null, model, user);

        assertSame(report, model.getAttribute("report"));
    }

    @Test
    void generateWithoutAChoiceAsksForAnEditor() {
        when(reportService.editorOptions(USER)).thenReturn(TWO_EDITORS);

        controller().editorEquipment(null, "true", model, user);

        assertEquals("Select an editor.", model.getAttribute("errorMessage"));
        assertNull(model.getAttribute("report"));
        verify(reportService, never()).generateEditorReport(any(), any());
    }

    @Test
    void showsTheReasonWhenTheChosenEditorCannotBeUsed() {
        when(reportService.editorOptions(USER)).thenReturn(TWO_EDITORS);
        when(reportService.generateEditorReport(9L, USER))
                .thenThrow(new InvalidReportSelectionException("The selected editor no longer exists."));

        controller().editorEquipment(9L, "true", model, user);

        assertEquals("The selected editor no longer exists.", model.getAttribute("errorMessage"));
        assertNull(model.getAttribute("report"));
    }

    // ------------------------------------------------------------------ UC-39

    private static final List<Option> TWO_PROPERTIES =
            List.of(new Option(10L, "North Site"), new Option(11L, "South Site"));

    private static PropertyEquipmentReport propertyReport() {
        return new PropertyEquipmentReport("North Site", "North", "Ana Silva", List.of(), "2026-10-24 12:00:00 UTC");
    }

    @Test
    void thePropertyFormIsShownWithTheOptionsAndNoReportYet() {
        when(reportService.propertyOptions(USER)).thenReturn(TWO_PROPERTIES);

        String view = controller().propertyEquipment(null, null, model, user);

        assertEquals("reports/property-equipment", view);
        assertEquals(TWO_PROPERTIES, model.getAttribute("properties"));
        assertNull(model.getAttribute("selectedPropertyId"));
        assertNull(model.getAttribute("report"));
        verify(reportService, never()).generatePropertyReport(any(), any());
    }

    @Test
    void anEditorWithOnlyOnePropertySeesItPreselected() {
        when(reportService.propertyOptions(USER)).thenReturn(List.of(new Option(10L, "North Site")));

        controller().propertyEquipment(null, null, model, user);

        assertEquals(10L, model.getAttribute("selectedPropertyId"));
        assertNull(model.getAttribute("report"));
    }

    @Test
    void generateShowsThePropertyReport() {
        when(reportService.propertyOptions(USER)).thenReturn(TWO_PROPERTIES);
        PropertyEquipmentReport report = propertyReport();
        when(reportService.generatePropertyReport(10L, USER)).thenReturn(report);

        controller().propertyEquipment(10L, "true", model, user);

        assertSame(report, model.getAttribute("report"));
        assertEquals(10L, model.getAttribute("selectedPropertyId"));
    }

    @Test
    void aLinkThatCarriesThePropertyAlsoGeneratesTheReport() {
        when(reportService.propertyOptions(USER)).thenReturn(TWO_PROPERTIES);
        PropertyEquipmentReport report = propertyReport();
        when(reportService.generatePropertyReport(11L, USER)).thenReturn(report);

        controller().propertyEquipment(11L, null, model, user);

        assertSame(report, model.getAttribute("report"));
    }

    @Test
    void generateWithoutAChoiceAsksForAProperty() {
        when(reportService.propertyOptions(USER)).thenReturn(TWO_PROPERTIES);

        controller().propertyEquipment(null, "true", model, user);

        assertEquals("Select a property.", model.getAttribute("errorMessage"));
        assertNull(model.getAttribute("report"));
        verify(reportService, never()).generatePropertyReport(any(), any());
    }

    @Test
    void showsTheReasonWhenTheChosenPropertyCannotBeUsed() {
        when(reportService.propertyOptions(USER)).thenReturn(TWO_PROPERTIES);
        when(reportService.generatePropertyReport(9L, USER))
                .thenThrow(new InvalidReportSelectionException("The selected property no longer exists."));

        controller().propertyEquipment(9L, "true", model, user);

        assertEquals("The selected property no longer exists.", model.getAttribute("errorMessage"));
        assertNull(model.getAttribute("report"));
    }

    // ------------------------------------------------------------------ UC-40

    private static final List<Option> TWO_REGIONS = List.of(new Option(5L, "North"), new Option(6L, "East"));

    private static RegionEquipmentReport regionReport(boolean restricted) {
        return new RegionEquipmentReport("North", List.of(), "2026-10-24 12:00:00 UTC", restricted);
    }

    @Test
    void theRegionFormIsShownWithTheOptionsAndNoReportYet() {
        when(reportService.regionOptions(USER)).thenReturn(TWO_REGIONS);

        String view = controller().regionEquipment(null, null, model, user);

        assertEquals("reports/region-equipment", view);
        assertEquals(TWO_REGIONS, model.getAttribute("regions"));
        assertNull(model.getAttribute("selectedRegionId"));
        assertNull(model.getAttribute("report"));
        verify(reportService, never()).generateRegionReport(any(), any());
    }

    @Test
    void anEditorInOnlyOneRegionSeesItPreselected() {
        when(reportService.regionOptions(USER)).thenReturn(List.of(new Option(5L, "North")));

        controller().regionEquipment(null, null, model, user);

        assertEquals(5L, model.getAttribute("selectedRegionId"));
        assertNull(model.getAttribute("report"));
    }

    @Test
    void generateShowsTheRegionReport() {
        when(reportService.regionOptions(USER)).thenReturn(TWO_REGIONS);
        RegionEquipmentReport report = regionReport(true);
        when(reportService.generateRegionReport(5L, USER)).thenReturn(report);

        controller().regionEquipment(5L, "true", model, user);

        assertSame(report, model.getAttribute("report"));
        assertEquals(5L, model.getAttribute("selectedRegionId"));
    }

    @Test
    void aLinkThatCarriesTheRegionAlsoGeneratesTheReport() {
        when(reportService.regionOptions(USER)).thenReturn(TWO_REGIONS);
        RegionEquipmentReport report = regionReport(false);
        when(reportService.generateRegionReport(6L, USER)).thenReturn(report);

        controller().regionEquipment(6L, null, model, user);

        assertSame(report, model.getAttribute("report"));
    }

    @Test
    void generateWithoutAChoiceAsksForARegion() {
        when(reportService.regionOptions(USER)).thenReturn(TWO_REGIONS);

        controller().regionEquipment(null, "true", model, user);

        assertEquals("Select a region.", model.getAttribute("errorMessage"));
        assertNull(model.getAttribute("report"));
        verify(reportService, never()).generateRegionReport(any(), any());
    }

    @Test
    void showsTheReasonWhenTheChosenRegionCannotBeUsed() {
        when(reportService.regionOptions(USER)).thenReturn(TWO_REGIONS);
        when(reportService.generateRegionReport(9L, USER))
                .thenThrow(new InvalidReportSelectionException("The selected region no longer exists."));

        controller().regionEquipment(9L, "true", model, user);

        assertEquals("The selected region no longer exists.", model.getAttribute("errorMessage"));
        assertNull(model.getAttribute("report"));
    }

    // ------------------------------------------------------------------ UC-41

    @Test
    void theAllEquipmentPageShowsOnlyTheGenerateButtonAtFirst() {
        String view = controller().allEquipment(null, model, user);

        assertEquals("reports/all-equipment", view);
        assertNull(model.getAttribute("report"));
        verify(reportService, never()).generateAllEquipmentReport(any());
    }

    @Test
    void generateShowsTheAllEquipmentReport() {
        AllEquipmentReport report = new AllEquipmentReport(List.of(), "2026-10-24 12:00:00 UTC", false);
        when(reportService.generateAllEquipmentReport(USER)).thenReturn(report);

        controller().allEquipment("true", model, user);

        assertSame(report, model.getAttribute("report"));
    }
}
