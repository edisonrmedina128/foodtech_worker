package com.foodtech.kitchen.worker.foodtech_worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FoodtechWorkerApplication {

	public static void main(String[] args) {
		SpringApplication.run(FoodtechWorkerApplication.class, args);
	}

}
