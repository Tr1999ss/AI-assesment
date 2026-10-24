package com.shk.generic.ingestion.category;

import com.shk.generic.category.CategoryDefinition;
import com.shk.generic.category.InvalidCategoryException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.shk.generic.ingestion.category.CategoryTestSupport.document;
import static com.shk.generic.ingestion.category.CategoryTestSupport.registry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SuppliedCategoryResolverTest {

    private final SuppliedCategoryResolver resolver = new SuppliedCategoryResolver(registry(
            new CategoryDefinition("hr", "people", true),
            new CategoryDefinition("legal", "contracts", true),
            new CategoryDefinition("finance", "money", true),
            new CategoryDefinition("archive", "retired", false)));

    @Test
    void firstListedIsPrimaryAndOrderIsKept() {
        CategoryResolution r = resolver.resolve(document("text", List.of("Legal", "hr", "finance", "hr")));

        assertThat(r.source()).isEqualTo(CategorySource.CALLER);
        assertThat(r.categories().primary()).isEqualTo("legal");
        assertThat(r.categories().all()).containsExactly("legal", "hr", "finance");
    }

    @Test
    void unknownCategoryIsRejectedWithValidNames() {
        assertThatThrownBy(() -> resolver.resolve(document("text", List.of("hr", "payroll"))))
                .isInstanceOf(InvalidCategoryException.class)
                .hasMessageContaining("[payroll]")
                .hasMessageContaining("[hr, legal, finance]");
    }

    @Test
    void inactiveCategoryIsRejected() {
        assertThatThrownBy(() -> resolver.resolve(document("text", List.of("archive"))))
                .isInstanceOf(InvalidCategoryException.class)
                .hasMessageContaining("[archive]")
                .hasMessageEndingWith("valid categories are [hr, legal, finance]");
    }
}
