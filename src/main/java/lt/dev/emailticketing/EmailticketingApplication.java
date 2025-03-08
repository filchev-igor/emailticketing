package lt.dev.emailticketing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EmailticketingApplication {

	public static void main(String[] args) {
		SpringApplication.run(EmailticketingApplication.class, args);
	}

}
