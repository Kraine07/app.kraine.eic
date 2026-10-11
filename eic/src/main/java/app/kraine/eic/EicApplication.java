package app.kraine.eic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan(basePackages = "app.kraine")
@SpringBootApplication
public class EicApplication {

	public static void main(String[] args) {
		SpringApplication.run(EicApplication.class, args);
	}

}
