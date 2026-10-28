package com.suwasarana.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class ApiApplication {

	public static void main(String[] args) {
		// Pin the JVM zone so timestamps behave identically on a laptop (Asia/Colombo) and in a container (UTC).
		// "Business time" (quiet hours, today, reminder windows) uses the injected Clock for Asia/Colombo instead.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(ApiApplication.class, args);
	}

}
