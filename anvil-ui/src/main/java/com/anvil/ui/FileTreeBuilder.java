package com.anvil.ui;

import javafx.scene.control.TreeItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds a directory tree from flat workspace paths. */
final class FileTreeBuilder {

    record FileNode(String label, String fullPath, boolean directory) {}

    private FileTreeBuilder() {}

    /** Backward-compatible: every path is treated as a file. */
    static TreeItem<FileNode> build(List<String> filePaths) {
        return build(filePaths, Set.copyOf(filePaths), "workspace");
    }

    static TreeItem<FileNode> build(List<String> allPaths, Set<String> filePaths, String rootLabel) {
        TreeItem<FileNode> root = new TreeItem<>(new FileNode(rootLabel, null, true));
        root.setExpanded(true);

        Map<String, TreeItem<FileNode>> dirIndex = new LinkedHashMap<>();
        dirIndex.put("", root);

        List<String> sorted = new ArrayList<>(allPaths);
        sorted.sort(Comparator.naturalOrder());

        for (String path : sorted) {
            if (path == null || path.isBlank()) {
                continue;
            }
            boolean isFile = filePaths.contains(path);
            String[] parts = path.split("/");
            StringBuilder prefix = new StringBuilder();
            TreeItem<FileNode> parent = root;

            for (int i = 0; i < parts.length; i++) {
                boolean isLast = i == parts.length - 1;
                if (prefix.length() > 0) {
                    prefix.append('/');
                }
                prefix.append(parts[i]);
                String key = prefix.toString();

                if (isLast && isFile) {
                    parent.getChildren().add(new TreeItem<>(new FileNode(parts[i], path, false)));
                } else {
                    TreeItem<FileNode> dir = dirIndex.get(key);
                    if (dir == null) {
                        dir = new TreeItem<>(new FileNode(parts[i], key, true));
                        parent.getChildren().add(dir);
                        dirIndex.put(key, dir);
                    }
                    parent = dir;
                }
            }
        }
        sortRecursive(root);
        return root;
    }

    private static void sortRecursive(TreeItem<FileNode> node) {
        node.getChildren().sort(Comparator.comparing(
                (TreeItem<FileNode> t) -> !t.getValue().directory()).thenComparing(t -> t.getValue().label()));
        for (TreeItem<FileNode> child : node.getChildren()) {
            if (child.getValue().directory()) {
                sortRecursive(child);
            }
        }
    }
}
