package com.shk.generic.ingestion.intake.mcp;

import com.shk.generic.ingestion.IngestionResult;
import com.shk.generic.ingestion.intake.IntakeGateway;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IngestionMcpTools {

    private final IntakeGateway gateway;
    private final McpIntakeAdapter adapter;

    public IngestionMcpTools(IntakeGateway gateway, McpIntakeAdapter adapter) {
        this.gateway = gateway;
        this.adapter = adapter;
    }

    @McpTool(name = "ingest_document",
            description = "Add a document to the knowledge base. Send plain/Markdown/JSON content as 'text', "
                    + "or binary files such as PDF as 'base64'. Omit 'categories' to have them classified automatically.")
    public IngestionResult ingestDocument(
            @McpToolParam(description = "Document name, ideally with extension, e.g. handbook.pdf") String name,
            @McpToolParam(description = "Text content; mutually exclusive with base64", required = false) String text,
            @McpToolParam(description = "Base64-encoded file bytes; mutually exclusive with text", required = false) String base64,
            @McpToolParam(description = "MIME type, e.g. application/pdf", required = false) String mimeType,
            @McpToolParam(description = "Category names, primary first; omit for automatic classification", required = false) List<String> categories) {
        return gateway.submit(adapter, new McpIngestArgs(name, text, base64, mimeType, categories));
    }
}
