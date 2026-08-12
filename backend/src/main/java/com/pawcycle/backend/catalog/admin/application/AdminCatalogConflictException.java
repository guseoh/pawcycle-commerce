package com.pawcycle.backend.catalog.admin.application;

public class AdminCatalogConflictException extends AdminCatalogException {
	public AdminCatalogConflictException(String code, String message) {
		super(code, message);
	}
}
