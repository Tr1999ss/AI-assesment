package com.shk.generic.ingestion.extract.reader;

import com.shk.generic.ingestion.extract.DocumentExtractor;
import com.shk.generic.ingestion.intake.SourceFormat;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A top-level array becomes one Document per element; anything else becomes one Document.
 * Each Document's text is the element flattened to {@code path.to.field: value} lines,
 * which embeds far better than raw JSON or a Java {@code Map.toString()}.
 * (Spring AI's {@code JsonReader} is not used: without key selection it emits {@code Map.toString()}
 * and it cannot handle arrays of primitives.)
 */
@Component
public class JsonExtractor implements DocumentExtractor {

    public static final String JSON_INDEX = "json_index";

    private final JsonMapper jsonMapper;

    public JsonExtractor(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean supports(SourceFormat format) {
        return format == SourceFormat.JSON;
    }

    @Override
    public List<Document> extract(Resource content, String filename) {
        JsonNode root = read(content);
        if (!root.isArray()) {
            return List.of(new Document(flatten(root)));
        }
        List<Document> docs = new ArrayList<>();
        int index = 0;
        for (JsonNode element : root) {
            docs.add(new Document(flatten(element), Map.of(JSON_INDEX, index++)));
        }
        return docs;
    }

    private JsonNode read(Resource content) {
        try (InputStream in = content.getInputStream()) {
            return jsonMapper.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String flatten(JsonNode node) {
        StringBuilder sb = new StringBuilder();
        flatten("", node, sb);
        return sb.toString().strip();
    }

    private static void flatten(String path, JsonNode node, StringBuilder sb) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                flatten(path.isEmpty() ? field.getKey() : path + "." + field.getKey(), field.getValue(), sb);
            }
        } else if (node.isArray()) {
            int i = 0;
            for (JsonNode element : node) {
                flatten(path + "[" + i++ + "]", element, sb);
            }
        } else if (!node.isNull()) {
            if (!path.isEmpty()) {
                sb.append(path).append(": ");
            }
            sb.append(node.asString()).append('\n');
        }
    }
}
