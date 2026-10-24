package com.shk.generic.retrieval.eval;

import com.shk.generic.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real retrieval quality: the sample corpus is embedded with the configured embedding model and every default
 * configuration is evaluated with the real chat model, in a throwaway database (the dev knowledge base is not
 * touched). Opt-in because it calls paid APIs:
 * <pre>./mvnw test -Dtest=LiveEvaluationTests -Deval.live=true</pre>
 * Needs OPENROUTER_API_KEY. Writes target/eval/live-report.md.
 */
@EnabledIfSystemProperty(named = "eval.live", matches = "true",
        disabledReason = "live evaluation calls paid model APIs; run with -Deval.live=true")
@SpringBootTest(properties = "ingestion.store.sweeper-enabled=false")
@Import(TestcontainersConfiguration.class)
class LiveEvaluationTests {

    @Autowired
    EvaluationService evaluation;

    @Test
    void evaluateEveryDefaultConfigurationAgainstSemanticBaseline() throws Exception {
        GoldenSet set = evaluation.loadDefault();
        int seeded = evaluation.seedCorpus(set);
        // -Deval.configurations=semantic,semantic+hyde runs a subset (the first is the baseline)
        String subset = System.getProperty("eval.configurations", "");
        List<EvalConfig> configs = subset.isBlank()
                ? evaluation.defaultConfigurations()
                : Arrays.stream(subset.split(",")).map(String::trim).map(EvalConfig::parse).toList();
        EvaluationReport report = evaluation.evaluate(set, configs, 5, true, seeded);

        Path out = Path.of("target", "eval", "live-report.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report.toMarkdown());
        System.out.println(report.toMarkdown());

        assertThat(report.baseline()).isEqualTo("semantic");
        assertThat(report.results()).allSatisfy(r -> assertThat(r.errors()).as(r.configuration()).isZero());
    }
}
