package com.shk.generic;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Full application context against a Testcontainers database.
 * Chat and embedding models are replaced by stubs, so no test ever calls a real model.
 * The scheduled sweeper is off; tests call it directly.
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "ingestion.store.sweeper-enabled=false"
})
@Import({TestcontainersConfiguration.class, AbstractIntegrationTest.StubModelsConfiguration.class})
public abstract class AbstractIntegrationTest {

    @Autowired
    protected StubChatModel chatModel;

    @Autowired
    protected StubEmbeddingModel embeddingModel;

    @BeforeEach
    void resetModels() {
        chatModel.reset();
        embeddingModel.reset();
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class StubModelsConfiguration {

        @Bean
        @Primary
        StubChatModel stubChatModel() {
            return new StubChatModel();
        }

        @Bean
        @Primary
        StubEmbeddingModel stubEmbeddingModel() {
            return new StubEmbeddingModel();
        }
    }
}
