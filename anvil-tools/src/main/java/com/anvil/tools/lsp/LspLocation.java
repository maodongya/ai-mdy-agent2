package com.anvil.tools.lsp;

/** Source location for go-to-definition / find-references. */
public record LspLocation(String path, int line, int column, String source) {}
