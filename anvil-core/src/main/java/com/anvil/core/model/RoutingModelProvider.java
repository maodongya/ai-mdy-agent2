package com.anvil.core.model;

import com.anvil.core.loop.RunProfile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Delegates each turn to a model chosen by {@link ModelRouter}. */
public final class RoutingModelProvider implements ModelProvider {

    private final String baseModel;
    private final ModelRoutingConfig routing;
    private final LlmRegistry registry;
    private final Path fixturesRoot;
    private final RunProfile profile;
    private final java.util.function.BiConsumer<Integer, String> onRoute;
    private final Map<String, ModelProvider> cache = new ConcurrentHashMap<>();

    public RoutingModelProvider(
            String baseModel,
            ModelRoutingConfig routing,
            LlmRegistry registry,
            Path fixturesRoot,
            RunProfile profile,
            java.util.function.BiConsumer<Integer, String> onRoute) {
        this.baseModel = baseModel;
        this.routing = routing;
        this.registry = registry;
        this.fixturesRoot = fixturesRoot;
        this.profile = profile;
        this.onRoute = onRoute == null ? (s, m) -> {} : onRoute;
    }

    @Override
    public Optional<ModelTurn> nextTurn(ModelTurnContext context) {
        int step = estimateStep(context.history());
        ModelRouter.StepKind kind = ModelRouter.classify(context.history(), profile, step);
        String modelId = ModelRouter.route(baseModel, kind, routing);
        onRoute.accept(step, modelId + " [" + ModelRouter.stepKindWire(kind) + "]");
        return provider(modelId).nextTurn(context);
    }

    private ModelProvider provider(String modelId) {
        return cache.computeIfAbsent(modelId, id -> {
            try {
                return ModelProviderFactory.create(id, registry, fixturesRoot);
            } catch (Exception e) {
                throw new IllegalStateException("model provider failed: " + id, e);
            }
        });
    }

    private static int estimateStep(List<Map<String, Object>> history) {
        int assistants = 0;
        for (Map<String, Object> msg : history) {
            if ("assistant".equals(msg.get("role"))) {
                assistants++;
            }
        }
        return Math.max(1, assistants + 1);
    }
}
