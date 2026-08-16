package com.anvil.ui;

import javafx.scene.Scene;

import java.util.List;

/** 统一编辑器主题：通过向 Scene 注入不同的样式表实现一键切换。 */
final class AnvilThemes {

    private AnvilThemes() {}

    /** 主题名称列表（与样式表资源一一对应）。 */
    static final List<THEME> THEMES = List.of(
            new THEME("dark", "/themes/dark.css", "Dark"),
            new THEME("light", "/themes/light.css", "Light"),
            new THEME("monokai", "/themes/monokai.css", "Monokai"));

    record THEME(String id, String stylesheet, String label) {}

    /** 将主题样式表安装到场景，替换旧的 anvil 主题样式。 */
    static void apply(Scene scene, String themeId) {
        THEME theme = THEMES.stream()
                .filter(t -> t.id().equals(themeId))
                .findFirst()
                .orElse(THEMES.get(0));
        scene.getStylesheets().removeIf(s -> s.contains("/themes/"));
        scene.getStylesheets().add(WorkbenchWindow.class.getResource(theme.stylesheet()).toExternalForm());
    }
}
