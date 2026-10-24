package com.shk.generic.ingestion.intake;

/** How a document arrived. Independent of what format it is. */
public enum IntakeChannel {
    MULTIPART,
    JSON_TEXT,
    MCP
}
