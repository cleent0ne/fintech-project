package com.cleentone.fintech;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The main entry point for the Fintech Payment API.
 * We've enabled scheduling here so we can run background tasks like 
 * cleaning up the token blacklist.
 */
@SpringBootApplication
@EnableScheduling  
public class FintechApplication {

	public static void main(String[] args) {
		SpringApplication.run(FintechApplication.class, args);
	}

}
