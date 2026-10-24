package com.shk.generic.retrieval.web;

import com.shk.generic.retrieval.RetrievalRequestException;
import com.shk.generic.retrieval.RetrievalUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class RetrievalExceptionHandler {

    @ExceptionHandler(RetrievalRequestException.class)
    public ProblemDetail handleBadRequest(RetrievalRequestException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid search", e.getMessage());
    }

    @ExceptionHandler(RetrievalUnavailableException.class)
    public ProblemDetail handleUnavailable(RetrievalUnavailableException e) {
        // Provider errors can carry URLs, keys or quota details: log them, don't return them.
        log.warn("Retrieval failed", e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Search unavailable",
                "Search or answering is temporarily unavailable. Retry later.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
