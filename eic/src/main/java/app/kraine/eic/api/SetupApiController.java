package app.kraine.eic.api;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.kraine.eic.form.SetupForm;
import app.kraine.eic.model.User;
import app.kraine.eic.service.FirstRunService;
import app.kraine.eic.validation.SetupFormValidator;

/** JSON version of the first-run setup, for the Flutter app. Both endpoints are public. */
@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
public class SetupApiController {

    private final FirstRunService firstRunService;
    private final SetupFormValidator setupFormValidator;

    public record Status(boolean setupRequired) {
    }

    public record CreatedAdministrator(Long id, String email, String role) {
    }

    @InitBinder("setupForm")
    void initBinder(WebDataBinder binder) {
        binder.addValidators(setupFormValidator);
    }

    /** The app calls this on start: true means it must show the setup form instead of the login. */
    @GetMapping("/status")
    public Status status() {
        return new Status(firstRunService.isSetupRequired());
    }

    /** 201 when created, 400 for invalid fields, 409 when a user already exists. */
    @PostMapping("/administrator")
    public ResponseEntity<CreatedAdministrator> createAdministrator(@Valid @RequestBody SetupForm form) {
        User admin = firstRunService.createFirstAdministrator(form);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreatedAdministrator(admin.getId(), admin.getEmail(), admin.getRole().getRoleType().name()));
    }
}
