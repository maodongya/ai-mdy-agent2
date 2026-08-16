package com.anvil.core.model;

import java.util.Optional;

public interface ModelProvider {

    Optional<ModelTurn> nextTurn(ModelTurnContext context);
}
