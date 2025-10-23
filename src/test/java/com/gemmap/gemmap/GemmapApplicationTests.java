package com.gemmap.gemmap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@AutoConfigureWebMvc
class GemmapApplicationTests {

	@Test
	void contextLoads() {
		// Spring 컨텍스트가 정상적으로 로드되는지 확인
	}

}
