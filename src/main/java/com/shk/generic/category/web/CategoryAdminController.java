package com.shk.generic.category.web;

import com.shk.generic.category.admin.CategoryAdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/** Category management. Protected by the admin API key (see {@code AdminApiKeyFilter}). */
@RestController
@RequestMapping("/api/admin/categories")
public class CategoryAdminController {

    private final CategoryAdminService service;

    public CategoryAdminController(CategoryAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<CategoryResponse> list(@RequestParam(defaultValue = "true") boolean includeInactive) {
        return service.list(includeInactive).stream().map(CategoryResponse::from).toList();
    }

    @GetMapping("/{name}")
    public CategoryResponse get(@PathVariable String name) {
        return CategoryResponse.from(service.get(name));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse created = CategoryResponse.from(service.create(request.name(), request.description()));
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{name}").buildAndExpand(created.name()).toUri())
                .body(created);
    }

    @PatchMapping("/{name}")
    public CategoryResponse update(@PathVariable String name, @Valid @RequestBody UpdateCategoryRequest request) {
        return CategoryResponse.from(service.update(name, request.description(), request.active()));
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        service.delete(name);
        return ResponseEntity.noContent().build();
    }
}
