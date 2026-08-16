package com.anvil.core.model;

import java.util.Map;

public record ToolCallIntent(String id, String name, Map<String, Object> arguments) {}
