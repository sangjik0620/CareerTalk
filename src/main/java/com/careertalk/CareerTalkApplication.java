package com.careertalk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class CareerTalkApplication {

	public static void main(String[] args) {
		SpringApplication.run(CareerTalkApplication.class, args);
	}

}
