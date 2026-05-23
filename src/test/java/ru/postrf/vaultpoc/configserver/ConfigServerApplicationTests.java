package ru.postrf.vaultpoc.configserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "composite"})
class ConfigServerApplicationTests {

    @Test
    void contextLoads() {
    }
}
