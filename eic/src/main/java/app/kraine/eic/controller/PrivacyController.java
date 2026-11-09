package app.kraine.eic.controller;


import app.kraine.eic.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Duration;

/**
 * The privacy policy page. It is public (signed-out visitors, the login page and the setup page link to it).
 *
 * <p>The retention period and the session timeout are read from the same settings the application really
 * uses, so the policy cannot drift from what the system does. The organisation details come from
 * application.properties; until they are set the page shows highlighted [placeholders].
 */
@Controller
@RequiredArgsConstructor
public class PrivacyController {

    private final AuditService auditService;

    @Value("${app.privacy.organization:[Organization name]}")
    private String organization;

    @Value("${app.privacy.contact-email:[privacy contact email]}")
    private String contactEmail;

    @Value("${app.privacy.effective-date:[effective date]}")
    private String effectiveDate;

    @Value("${app.privacy.hosting-location:[where the data is hosted]}")
    private String hostingLocation;

    @Value("${server.servlet.session.timeout:10m}")
    private Duration sessionTimeout = Duration.ofMinutes(10);

    @GetMapping("/privacy")
    public String privacy(Model model) {
        model.addAttribute("organization", organization);
        model.addAttribute("contactEmail", contactEmail);
        model.addAttribute("effectiveDate", effectiveDate);
        model.addAttribute("hostingLocation", hostingLocation);
        model.addAttribute("retentionDays", auditService.retentionDays());
        model.addAttribute("sessionMinutes", Math.max(1L, sessionTimeout.toMinutes()));
        return "privacy";
    }
}
