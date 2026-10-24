package com.shk.generic.category.web;

import com.shk.generic.AbstractIntegrationTest;
import com.shk.generic.TestTokens;
import com.shk.generic.ingestion.IngestionService;
import com.shk.generic.ingestion.intake.IngestionRequest;
import com.shk.generic.ingestion.intake.IntakeChannel;
import com.shk.generic.ingestion.intake.NamedByteArrayResource;
import com.shk.generic.ingestion.intake.SourceFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class CategoryAdminTests extends AbstractIntegrationTest {

    private static final String ADMIN = "/api/admin/categories";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    IngestionService ingestionService;

    private final String name = "t-" + UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM document_registry WHERE :n = ANY(categories)").param("n", name).update();
        jdbc.sql("DELETE FROM category WHERE name = :n").param("n", name).update();
    }

    @Test
    void adminEndpointsRequireTheAdminRole() {
        assertThat(mvc.get().uri(ADMIN)).hasStatus(401);
        assertThat(mvc.get().uri(ADMIN).with(TestTokens.editor())).hasStatus(403);
        assertThat(mvc.get().uri(ADMIN).with(TestTokens.admin())).hasStatusOk();
        assertThat(mvc.get().uri("/api/categories")).as("public listing needs no token").hasStatusOk();
    }

    @Test
    void createdCategoryIsImmediatelyUsableForIngestionAndListing() {
        MvcTestResult created = create(name, "Sales playbooks and pricing negotiations.");

        assertThat(created).hasStatus(201).headers().hasValue("Location", "http://localhost" + ADMIN + "/" + name);
        assertThat(created).bodyJson().extractingPath("$.active").isEqualTo(true);
        assertThat(mvc.get().uri("/api/categories")).bodyText().contains(name);
        assertThat(ingestionService.ingest(request(List.of(name))).primaryCategory()).isEqualTo(name);
    }

    @Test
    void namesAreValidatedAndUnique() {
        assertThat(create("Bad Name!", "x")).hasStatus(400);
        assertThat(create(name, "  ")).hasStatus(400);
        assertThat(create(name, "first")).hasStatus(201);
        assertThat(create(name, "second")).hasStatus(409).bodyText().contains("already exists");
    }

    @Test
    void patchUpdatesOnlyTheGivenFields() {
        create(name, "Original description.");

        assertThat(patch(name, "{\"description\":\"Better description.\"}")).hasStatusOk()
                .bodyJson().extractingPath("$.description").isEqualTo("Better description.");
        assertThat(patch(name, "{\"active\":false}")).hasStatusOk()
                .bodyJson().extractingPath("$.description").isEqualTo("Better description.");
        assertThat(patch(name, "{}")).hasStatus(400);
        assertThat(patch("does-not-exist", "{\"active\":true}")).hasStatus(404);
    }

    @Test
    void deactivatedCategoryIsHiddenAndRejectedForNewUploads() {
        create(name, "Temporary.");
        patch(name, "{\"active\":false}");

        assertThat(mvc.get().uri("/api/categories")).bodyText().doesNotContain(name);
        assertThat(mvc.get().uri(ADMIN + "?includeInactive=true").with(TestTokens.admin())).bodyText().contains(name);
        assertThat(mvc.post().uri("/api/ingest/text").with(TestTokens.editor()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"text\":\"" + UUID.randomUUID() + "\",\"categories\":[\"" + name + "\"]}"))
                .hasStatus(400);
    }

    @Test
    void fallbackCategoryCannotBeDeactivatedOrDeleted() {
        assertThat(patch("general", "{\"active\":false}")).hasStatus(409).bodyText().contains("fallback");
        assertThat(delete("general")).hasStatus(409);
        assertThat(patch("general", "{\"description\":\"Still editable.\"}")).hasStatusOk();
        patch("general", "{\"description\":\"Anything that does not clearly fit another category.\"}");
    }

    @Test
    void onlyUnusedCategoriesCanBeDeleted() {
        create(name, "Will be used.");
        ingestionService.ingest(request(List.of(name)));

        assertThat(delete(name)).hasStatus(409).bodyText().contains("used by 1 document").contains("deactivate");

        jdbc.sql("DELETE FROM document_registry WHERE :n = ANY(categories)").param("n", name).update();
        assertThat(delete(name)).hasStatus(204);
        assertThat(mvc.get().uri(ADMIN + "/" + name).with(TestTokens.admin())).hasStatus(404);
        assertThat(delete(name)).hasStatus(404);
    }

    private MvcTestResult create(String categoryName, String description) {
        return mvc.post().uri(ADMIN).with(TestTokens.admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + categoryName + "\",\"description\":\"" + description + "\"}").exchange();
    }

    private MvcTestResult patch(String categoryName, String body) {
        return mvc.patch().uri(ADMIN + "/" + categoryName).with(TestTokens.admin())
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private MvcTestResult delete(String categoryName) {
        return mvc.delete().uri(ADMIN + "/" + categoryName).with(TestTokens.admin()).exchange();
    }

    private static IngestionRequest request(List<String> categories) {
        String text = "Admin test " + UUID.randomUUID();
        return IngestionRequest.of(IntakeChannel.JSON_TEXT, "admin.txt", SourceFormat.TEXT,
                new NamedByteArrayResource(text.getBytes(StandardCharsets.UTF_8), "admin.txt"), categories, null);
    }
}
