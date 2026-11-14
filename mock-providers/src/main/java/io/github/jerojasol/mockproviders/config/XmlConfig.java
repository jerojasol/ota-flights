package io.github.jerojasol.mockproviders.config;

import org.springframework.boot.jackson.autoconfigure.XmlMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.dataformat.xml.XmlWriteFeature;

@Configuration(proxyBeanMethods = false)
public class XmlConfig {

    /** Beta's contract starts every document with an XML declaration. */
    @Bean
    XmlMapperBuilderCustomizer xmlDeclaration() {
        return builder -> builder.enable(XmlWriteFeature.WRITE_XML_DECLARATION);
    }
}
