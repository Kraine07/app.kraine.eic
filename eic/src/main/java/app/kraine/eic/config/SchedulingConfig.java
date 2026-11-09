package app.kraine.eic.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled jobs. Remove this class if scheduling is already enabled elsewhere. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
