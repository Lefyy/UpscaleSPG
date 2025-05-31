package upscale_project.UpscaleSPG;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class UpscaleSpgApplication {

	public static void main(String[] args) {
		SpringApplication.run(UpscaleSpgApplication.class, args);
	}

}
