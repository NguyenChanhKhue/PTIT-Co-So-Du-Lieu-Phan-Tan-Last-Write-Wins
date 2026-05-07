package com.example.distributed_last_write_wins;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class DistributedLastWriteWinsApplication {

	public static void main(String[] args) {
		SpringApplication.run(DistributedLastWriteWinsApplication.class, args);
	}

	@Bean
	public RestTemplate restTemplate(RestTemplateBuilder builder) {
		return builder
				.setConnectTimeout(java.time.Duration.ofSeconds(5))
				.setReadTimeout(java.time.Duration.ofSeconds(10))
				.build();
	}
}
