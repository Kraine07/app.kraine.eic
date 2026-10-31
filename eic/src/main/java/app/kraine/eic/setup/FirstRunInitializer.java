package app.kraine.eic.setup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import app.kraine.eic.service.FirstRunService;

/** UA-19: checks for existing users every time the application starts. */
@Slf4j
@Component
@RequiredArgsConstructor
public class FirstRunInitializer implements ApplicationRunner {

    private final FirstRunService firstRunService;

    @Override
    public void run(ApplicationArguments args) {
        if (firstRunService.isSetupRequired()) {
            log.warn("No user accounts exist. First-run setup is required: open /setup to create the "
                    + "first administrator account.");
        } else {
            log.info("User accounts found. First-run setup is not required.");
        }
    }
}