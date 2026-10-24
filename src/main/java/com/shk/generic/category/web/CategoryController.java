package com.shk.generic.category.web;

import com.shk.generic.category.CategoryRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public, read-only: the active categories callers can tag uploads with or filter searches by. */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRegistry registry;

    public CategoryController(CategoryRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<CategoryResponse> active() {
        return registry.active().stream().map(CategoryResponse::from).toList();
    }
}
