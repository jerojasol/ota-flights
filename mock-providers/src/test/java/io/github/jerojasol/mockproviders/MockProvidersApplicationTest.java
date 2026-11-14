package io.github.jerojasol.mockproviders;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "mock.chaos.enabled=false")
class MockProvidersApplicationTest {

    @Test
    void should_load_context() {
    }
}
