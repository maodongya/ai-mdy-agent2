package com.anvil.server.config;

import com.anvil.core.compact.ContextBudget;
import com.anvil.core.loop.LoopConfig;
import com.anvil.core.loop.RunProfile;
import com.anvil.core.loop.VerifyConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 服务器级上下文限制，来源于 {@code application.yml}，并与运行档案（run profile）合并。 */
@Component
public final class AnvilContextConfig {

    /** 触发上下文压缩的 token 阈值。 */
    private final int compactThresholdTokens;
    /** 压缩后的目标上下文 token 数。 */
    private final int contextTargetTokens;
    /** 压缩后保留的最近消息条数。 */
    private final int contextKeepRecent;
    /** 工具返回内容的单次最大字符数上限。 */
    private final int toolContentMaxChars;
    /** 默认的最大步骤数上限。 */
    private final int maxStepsDefault;
    /** 验证（verify）配置。 */
    private final VerifyConfig verifyConfig;
    /** 循环（loop）配置。 */
    private final LoopConfig loopConfig;

    /**
     * 构造函数：从 Spring 配置属性注入并组装各项运行时配置。
     *
     * @param compactThresholdTokens 压缩阈值（token）
     * @param contextTargetTokens    压缩后目标 token 数
     * @param contextKeepRecent      保留的最近消息条数
     * @param toolContentMaxChars    工具内容最大字符数
     * @param maxStepsDefault        默认最大步骤数
     * @param verifyAutoAfterWrite   写入后是否自动验证
     * @param verifyCommandTemplate  验证命令模板
     * @param verifyTimeoutMs        验证超时（毫秒）
     * @param verifyInjectFailures   是否注入失败作为测试
     * @param parallelReadTools      是否允许并行读取工具
     */
    public AnvilContextConfig(
            @Value("${anvil.compact-threshold-tokens:120000}") int compactThresholdTokens,
            @Value("${anvil.context-target-tokens:80000}") int contextTargetTokens,
            @Value("${anvil.context-keep-recent:24}") int contextKeepRecent,
            @Value("${anvil.tool-content-max-chars:16000}") int toolContentMaxChars,
            @Value("${anvil.max-steps-default:40}") int maxStepsDefault,
            @Value("${anvil.verify.auto-after-write:true}") boolean verifyAutoAfterWrite,
            @Value("${anvil.verify.command-template:}") String verifyCommandTemplate,
            @Value("${anvil.verify.timeout-ms:180000}") long verifyTimeoutMs,
            @Value("${anvil.verify.inject-failures:true}") boolean verifyInjectFailures,
            @Value("${anvil.loop.parallel-read-tools:true}") boolean parallelReadTools) {
        this.compactThresholdTokens = compactThresholdTokens;
        this.contextTargetTokens = contextTargetTokens;
        this.contextKeepRecent = contextKeepRecent;
        this.toolContentMaxChars = toolContentMaxChars;
        this.maxStepsDefault = maxStepsDefault;
        this.verifyConfig = new VerifyConfig(
                verifyAutoAfterWrite, verifyCommandTemplate, verifyTimeoutMs, verifyInjectFailures);
        this.loopConfig = new LoopConfig(parallelReadTools);
    }

    /** 返回基于服务器配置的基础 ContextBudget（默认预算）。 */
    public ContextBudget baseBudget() {
        return new ContextBudget(
                compactThresholdTokens, contextTargetTokens, contextKeepRecent, toolContentMaxChars);
    }

    /** 取服务器配置与所选运行档案两者中更高的上限值。 */
    public ContextBudget budgetForProfile(RunProfile profile) {
        ContextBudget base = baseBudget();
        ContextBudget preset = profile.contextBudget();
        return new ContextBudget(
                Math.max(base.compactThresholdTokens(), preset.compactThresholdTokens()),
                Math.max(base.targetTokensAfterCompact(), preset.targetTokensAfterCompact()),
                Math.max(base.keepRecentMessages(), preset.keepRecentMessages()),
                Math.max(base.maxToolContentChars(), preset.maxToolContentChars()));
    }

    /** 返回默认的最大步骤数。 */
    public int maxStepsDefault() {
        return maxStepsDefault;
    }

    /** 返回验证配置。 */
    public VerifyConfig verifyConfig() {
        return verifyConfig;
    }

    /** 返回循环配置。 */
    public LoopConfig loopConfig() {
        return loopConfig;
    }
}
