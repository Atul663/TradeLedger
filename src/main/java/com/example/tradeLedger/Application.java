package com.example.tradeLedger;

import com.example.tradeLedger.config.DatabaseUrl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Application {
	public static void main(String[] args) {
		// Render supplies a managed database as one libpq DATABASE_URL. Split it
		// into the DB_URL/DB_USER/DB_PASSWORD this app reads before the context
		// starts; a no-op anywhere those are already set.
		DatabaseUrl.applyIfPresent();
		SpringApplication.run(Application.class, args);
	}

}
