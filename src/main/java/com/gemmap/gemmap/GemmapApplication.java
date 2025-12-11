package com.gemmap.gemmap;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class GemmapApplication {

	public static void main(String[] args) {
		SpringApplication.run(GemmapApplication.class, args);
	}

	@PostConstruct
	public void init() {
		// JVM 기본 타임존을 Asia/Seoul로 설정
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
	}
}
