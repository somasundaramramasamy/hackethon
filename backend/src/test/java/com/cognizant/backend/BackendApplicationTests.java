package com.cognizant.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        // Override any real keys on the machine (env vars outrank application.properties).
        "GEMINI_API_KEY=", "ANTHROPIC_API_KEY=",
        "spring.ai.google.genai.api-key=test-placeholder", "spring.ai.anthropic.api-key="})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
