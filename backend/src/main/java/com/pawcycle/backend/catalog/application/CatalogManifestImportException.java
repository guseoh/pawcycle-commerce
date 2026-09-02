package com.pawcycle.backend.catalog.application;

public class CatalogManifestImportException extends RuntimeException {

  public CatalogManifestImportException(String message) {
    super(message);
  }

  public CatalogManifestImportException(String message, Throwable cause) {
    super(message, cause);
  }
}
