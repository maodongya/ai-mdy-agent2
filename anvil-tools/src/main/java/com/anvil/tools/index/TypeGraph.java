package com.anvil.tools.index;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Java type hierarchy derived from indexed symbols (Phase 6.2). */
public final class TypeGraph {

    private final Map<String, List<String>> interfaceImplementors = new HashMap<>();
    private final Map<String, List<String>> classSubtypes = new HashMap<>();

    private TypeGraph() {}

    public static TypeGraph from(WorkspaceIndex index) {
        TypeGraph graph = new TypeGraph();
        if (index == null) {
            return graph;
        }
        for (WorkspaceIndex.SymbolEntry sym : index.symbols()) {
            if (sym.interfaces() != null) {
                for (String iface : sym.interfaces()) {
                    String key = simpleName(iface);
                    graph.interfaceImplementors
                            .computeIfAbsent(key, k -> new ArrayList<>())
                            .add(sym.path() + ":" + sym.name());
                }
            }
            if (sym.superName() != null && !sym.superName().isBlank()) {
                String key = simpleName(sym.superName());
                graph.classSubtypes
                        .computeIfAbsent(key, k -> new ArrayList<>())
                        .add(sym.path() + ":" + sym.name());
            }
        }
        return graph;
    }

    public List<String> implementorsOf(String typeName) {
        if (typeName == null) {
            return List.of();
        }
        return List.copyOf(interfaceImplementors.getOrDefault(simpleName(typeName), List.of()));
    }

    public List<String> subtypesOf(String typeName) {
        if (typeName == null) {
            return List.of();
        }
        return List.copyOf(classSubtypes.getOrDefault(simpleName(typeName), List.of()));
    }

    private static String simpleName(String fqcn) {
        String s = fqcn.trim();
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : s;
    }
}
