package io.github.jerojasol.mockproviders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MockProvidersApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockProvidersApplication.class, args);
    }
}
