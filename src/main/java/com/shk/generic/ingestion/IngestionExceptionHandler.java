package com.shk.generic.ingestion;

import com.shk.generic.ingestion.category.classifier.ClassificationException;
import com.shk.generic.ingestion.chunk.ChunkingException;
import com.shk.generic.ingestion.extract.ExtractionException;
import com.shk.generic.ingestion.intake.IntakeRejectedException;
import com.shk.generic.ingestion.store.StorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class IngestionExceptionHandler {

    @ExceptionHandler(IntakeRejectedException.class)
    public ProblemDetail handleRejected(IntakeRejectedException e) {
        return problem(HttpStatus.BAD_REQUEST, "Intake rejected", e.getMessage());
    }

    @ExceptionHandler(ExtractionException.class)
    public ProblemDetail handleExtraction(ExtractionException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Extraction failed", e.getMessage());
    }

    @ExceptionHandler(ChunkingException.class)
    public ProblemDetail handleChunking(ChunkingException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Chunking failed", e.getMessage());
    }

    @ExceptionHandler(ClassificationException.class)
    public ProblemDetail handleClassification(ClassificationException e) {
        // The cause may carry provider details (URLs, quota info); log it, don't return it.
        log.warn("Classification failed", e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Classification unavailable",
                "Automatic categorization is temporarily unavailable. Retry later or supply categories explicitly.");
    }

    @ExceptionHandler(StorageException.class)
    public ProblemDetail handleStorage(StorageException e) {
        // Usually the embedding provider; same reasoning as classification: log details, return a generic message.
        log.warn("Storage failed", e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Storage unavailable",
                "The document could not be embedded or stored right now. Nothing was saved; retry later.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
