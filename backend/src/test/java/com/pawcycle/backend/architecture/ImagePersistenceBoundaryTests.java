package com.pawcycle.backend.architecture;

import com.pawcycle.backend.catalog.admin.persistence.ProductImageAdminPersistence;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ImagePersistenceBoundaryTests {
  @Test void imageStorageDoesNotOwnApplicationCacheInvalidation() {
    noClasses().should().dependOnClassesThat().haveFullyQualifiedName(
        "com.pawcycle.backend.catalog.product.application.ProductListCacheInvalidator")
        .check(new ClassFileImporter().importClasses(ProductImageAdminPersistence.class));
  }
}
