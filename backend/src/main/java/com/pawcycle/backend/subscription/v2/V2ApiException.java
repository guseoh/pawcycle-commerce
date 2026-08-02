package com.pawcycle.backend.subscription.v2;

public class V2ApiException extends RuntimeException {

	private final int status;
	private final String code;

	public V2ApiException(int status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public V2ApiException(int status, String code, String message, Throwable cause) {
		super(message, cause);
		this.status = status;
		this.code = code;
	}

	public int status() { return status; }
	public String code() { return code; }
}