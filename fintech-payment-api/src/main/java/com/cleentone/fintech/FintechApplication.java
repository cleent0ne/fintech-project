package com.cleentone.fintech;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // activates @Scheduled in TokenBlacklistService
public class FintechApplication {

	public static void main(String[] args) {
		SpringApplication.run(FintechApplication.class, args);
	}

}
