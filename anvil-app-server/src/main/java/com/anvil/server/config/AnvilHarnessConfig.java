package com.anvil.server.config;

import com.anvil.core.mcp.McpBridge;
import com.anvil.core.model.LlmRegistry;
import com.anvil.core.model.OpenAiConfig;
import com.anvil.protocol.SandboxTier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 装配 Harness（智能体运行引擎）相关的各种 Bean。 */
@Configuration
public class AnvilHarnessConfig {

    /**
     * 构建 LLM 注册表（LlmRegistry），注册 OpenAI 与 DeepSeek 两个模型提供方。
     *
     * @param openAiBaseUrl  OpenAI 基础 URL
     * @param openAiKeyEnv   OpenAI API Key 所在的环境变量名
     * @param openAiModel    OpenAI 默认模型 ID
     * @param deepSeekBaseUrl DeepSeek 基础 URL
     * @param deepSeekKeyEnv DeepSeek API Key 所在的环境变量名
     * @param deepSeekModel  DeepSeek 默认模型 ID
     * @return 包含 OpenAI 与 DeepSeek 的模型注册表
     */
    @Bean
    LlmRegistry llmRegistry(
            @Value("${anvil.model.base-url:https://api.openai.com/v1}") String openAiBaseUrl,
            @Value("${anvil.model.api-key-env:OPENAI_API_KEY}") String openAiKeyEnv,
            @Value("${anvil.model.model-id:gpt-4o-mini}") String openAiModel,
            @Value("${anvil.deepseek.base-url:https://api.deepseek.com/v1}") String deepSeekBaseUrl,
            @Value("${anvil.deepseek.api-key-env:DEEPSEEK_API_KEY}") String deepSeekKeyEnv,
            @Value("${anvil.deepseek.model-id:deepseek-chat}") String deepSeekModel) {
        OpenAiConfig openAi = new OpenAiConfig(
                openAiBaseUrl,
                OpenAiConfig.fromEnv(openAiModel, openAiKeyEnv, openAiBaseUrl).apiKey(),
                openAiModel,
                Duration.ofSeconds(120));
        OpenAiConfig deepSeek = new OpenAiConfig(
                deepSeekBaseUrl,
                OpenAiConfig.fromEnv(deepSeekModel, deepSeekKeyEnv, deepSeekBaseUrl).apiKey(),
                deepSeekModel,
                Duration.ofSeconds(120));
        return new LlmRegistry(openAi, deepSeek);
    }

    /** 暴露默认（OpenAI）的 OpenAiConfig，供需要直接使用的组件注入。 */
    @Bean
    OpenAiConfig openAiConfig(LlmRegistry llmRegistry) {
        return llmRegistry.openAi();
    }

    /**
     * 构建 MCP（Model Context Protocol）桥接器，连接外部 MCP 服务器。
     *
     * @param rpcTimeoutMs RPC 调用超时（毫秒）
     * @param allowlist    允许连接的服务器白名单
     * @return MCP 桥接器实例
     */
    @Bean(destroyMethod = "close")
    McpBridge mcpBridge(
            @Value("${anvil.mcp.rpc-timeout-ms:30000}") long rpcTimeoutMs,
            @Value("${anvil.mcp.allowlist:}") List<String> allowlist) {
        Set<String> allowed = new HashSet<>(allowlist == null ? List.of() : allowlist);
        return new McpBridge(List.of(), allowed, rpcTimeoutMs);
    }

    /** 解析沙箱安全级别（SandboxTier），用于控制工具可访问的资源范围。 */
    @Bean
    SandboxTier sandboxTier(@Value("${anvil.sandbox.tier:workspace_write}") String tier) {
        return SandboxTier.fromWire(tier);
    }
}
