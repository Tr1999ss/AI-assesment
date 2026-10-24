package com.shk.generic.category.web;

import com.shk.generic.category.InvalidCategoryException;
import com.shk.generic.category.admin.CategoryConflictException;
import com.shk.generic.category.admin.CategoryNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Category errors, wherever they surface (admin, ingestion, search). */
@RestControllerAdvice
public class CategoryExceptionHandler {

    @ExceptionHandler(InvalidCategoryException.class)
    public ProblemDetail handleInvalid(InvalidCategoryException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid categories", e.getMessage());
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ProblemDetail handleNotFound(CategoryNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Category not found", e.getMessage());
    }

    @ExceptionHandler(CategoryConflictException.class)
    public ProblemDetail handleConflict(CategoryConflictException e) {
        return problem(HttpStatus.CONFLICT, "Category conflict", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
