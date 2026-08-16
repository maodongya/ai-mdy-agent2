package com.anvil.server.config;

import com.anvil.core.compact.ContextBudget;
import com.anvil.core.loop.LoopConfig;
import com.anvil.core.loop.RunProfile;
import com.anvil.core.loop.VerifyConfig;
import com.anvil.core.model.ModelRoutingConfig;
import com.anvil.protocol.Mode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 服务器级上下文限制，来源于 {@code application.yml}，并与运行档案（run profile）合并。 */
@Component
public final class AnvilContextConfig {

    private final int compactThresholdTokens;
    private final int contextTargetTokens;
    private final int contextKeepRecent;
    private final int toolContentMaxChars;
    private final int maxStepsDefault;
    private final VerifyConfig verifyConfig;
    private final LoopConfig loopConfig;
    private final ModelRoutingConfig modelRouting;
    private final boolean parallelWrites;
    private final boolean exploreSubAgent;
    private final boolean plannerRequired;
    private final int exploreMaxSteps;
    private final int exploreMaxTokensBudget;
    private final long tokenBudgetPerRun;

    public AnvilContextConfig(
            @Value("${anvil.compact-threshold-tokens:120000}") int compactThresholdTokens,
            @Value("${anvil.context-target-tokens:80000}") int contextTargetTokens,
            @Value("${anvil.context-keep-recent:24}") int contextKeepRecent,
            @Value("${anvil.tool-content-max-chars:16000}") int toolContentMaxChars,
            @Value("${anvil.max-steps-default:40}") int maxStepsDefault,
            @Value("${anvil.verify.auto-after-write:false}") boolean verifyAutoAfterWrite,
            @Value("${anvil.verify.auto-compile-after-write:true}") boolean verifyAutoCompileAfterWrite,
            @Value("${anvil.verify.command-template:}") String verifyCommandTemplate,
            @Value("${anvil.verify.timeout-ms:90000}") long verifyTimeoutMs,
            @Value("${anvil.verify.inject-failures:true}") boolean verifyInjectFailures,
            @Value("${anvil.verify.force-fix-on-failure:true}") boolean verifyForceFixOnFailure,
            @Value("${anvil.loop.parallel-read-tools:true}") boolean parallelReadTools,
            @Value("${anvil.loop.parallel-writes:true}") boolean parallelWrites,
            @Value("${anvil.loop.explore-sub-agent:false}") boolean exploreSubAgent,
            @Value("${anvil.loop.planner-required:true}") boolean plannerRequired,
            @Value("${anvil.loop.explore-max-steps:4}") int exploreMaxSteps,
            @Value("${anvil.loop.explore-max-tokens-budget:8000}") int exploreMaxTokensBudget,
            @Value("${anvil.loop.token-budget-per-run:500000}") long tokenBudgetPerRun,
            @Value("${anvil.model.routing.enabled:true}") boolean modelRoutingEnabled,
            @Value("${anvil.model.routing.explore:deepseek:deepseek-chat}") String exploreModel,
            @Value("${anvil.model.routing.edit:deepseek:deepseek-chat}") String editModel,
            @Value("${anvil.model.routing.plan:deepseek:deepseek-chat}") String planModel) {
        this.compactThresholdTokens = compactThresholdTokens;
        this.contextTargetTokens = contextTargetTokens;
        this.contextKeepRecent = contextKeepRecent;
        this.toolContentMaxChars = toolContentMaxChars;
        this.maxStepsDefault = maxStepsDefault;
        this.parallelWrites = parallelWrites;
        this.exploreSubAgent = exploreSubAgent;
        this.plannerRequired = plannerRequired;
        this.exploreMaxSteps = exploreMaxSteps;
        this.exploreMaxTokensBudget = exploreMaxTokensBudget;
        this.tokenBudgetPerRun = tokenBudgetPerRun;
        this.verifyConfig = new VerifyConfig(
                verifyAutoAfterWrite,
                verifyCommandTemplate,
                verifyTimeoutMs,
                verifyInjectFailures,
                verifyAutoCompileAfterWrite,
                verifyForceFixOnFailure);
        this.loopConfig = new LoopConfig(
                parallelReadTools,
                parallelWrites,
                exploreSubAgent,
                plannerRequired,
                exploreMaxSteps,
                exploreMaxTokensBudget,
                tokenBudgetPerRun);
        this.modelRouting = new ModelRoutingConfig(modelRoutingEnabled, exploreModel, editModel, planModel);
    }

    public ContextBudget baseBudget() {
        return new ContextBudget(
                compactThresholdTokens, contextTargetTokens, contextKeepRecent, toolContentMaxChars);
    }

    public ContextBudget budgetForProfile(RunProfile profile) {
        ContextBudget base = baseBudget();
        ContextBudget preset = profile.contextBudget();
        return new ContextBudget(
                Math.max(base.compactThresholdTokens(), preset.compactThresholdTokens()),
                Math.max(base.targetTokensAfterCompact(), preset.targetTokensAfterCompact()),
                Math.max(base.keepRecentMessages(), preset.keepRecentMessages()),
                Math.max(base.maxToolContentChars(), preset.maxToolContentChars()));
    }

    public int maxStepsDefault() {
        return maxStepsDefault;
    }

    public VerifyConfig verifyConfig() {
        return verifyConfig;
    }

    public VerifyConfig verifyFor(Mode mode, RunProfile profile) {
        return VerifyConfig.forRun(verifyConfig, mode, profile);
    }

    public LoopConfig loopConfig() {
        return loopConfig;
    }

    public LoopConfig loopConfigForProfile(RunProfile profile) {
        LoopConfig preset = LoopConfig.forProfile(profile);
        return new LoopConfig(
                loopConfig.parallelReadTools(),
                parallelWrites && preset.parallelWrites(),
                exploreSubAgent && preset.exploreSubAgent(),
                plannerRequired && preset.plannerRequired(),
                exploreMaxSteps > 0 ? exploreMaxSteps : preset.exploreMaxSteps(),
                exploreMaxTokensBudget > 0 ? exploreMaxTokensBudget : preset.exploreMaxTokensBudget(),
                tokenBudgetPerRun > 0 ? tokenBudgetPerRun : preset.tokenBudgetPerRun());
    }

    public ModelRoutingConfig modelRouting() {
        return modelRouting;
    }
}
