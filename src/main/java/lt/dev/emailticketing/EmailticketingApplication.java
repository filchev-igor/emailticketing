package lt.dev.emailticketing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableScheduling
public class EmailticketingApplication {

	public static void main(String[] args) {
		SpringApplication.run(EmailticketingApplication.class, args);
	}

	// ✅ Register RestTemplate so Spring can inject it
	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}
}
