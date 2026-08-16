package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorInfo(
        String code,
        String message,
        boolean retryable,
        Map<String, Object> details) {

    public ErrorInfo {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        if (message == null) {
            message = "";
        }
    }

    public static ErrorInfo of(String code, String message, boolean retryable) {
        return new ErrorInfo(code, message, retryable, null);
    }
}
