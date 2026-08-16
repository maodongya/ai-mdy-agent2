package com.anvil.core.prompt;

import java.util.List;
import java.util.Map;

public record PromptBundle(String instructions, List<Map<String, Object>> tools, List<Map<String, Object>> input) {}
