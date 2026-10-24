package com.shk.generic.docs;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DocsExceptionHandler {

    @ExceptionHandler(DocNotFoundException.class)
    public ProblemDetail handleNotFound(DocNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Document not found");
        return problem;
    }
}
