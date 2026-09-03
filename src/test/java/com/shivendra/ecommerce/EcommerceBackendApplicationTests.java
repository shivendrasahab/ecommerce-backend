package com.shivendra.ecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Boots the full context against H2 (no Redis, no seed) to catch wiring mistakes early. */
@SpringBootTest
@ActiveProfiles("test")
class EcommerceBackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
