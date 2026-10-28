package com.scaleway.mailsender.scaleway;

public class ScalewayApiException extends RuntimeException {

    private final int statusCode;
    private final boolean retryable;
    private final String rawResponse;

    public ScalewayApiException(String message, int statusCode, boolean retryable, String rawResponse) {
        super(message);
        this.statusCode = statusCode;
        this.retryable = retryable;
        this.rawResponse = rawResponse;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public String getRawResponse() {
        return rawResponse;
    }
}
