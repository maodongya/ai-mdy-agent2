package com.anvil.tools.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenModuleGraphTest {

    @Test
    void parsesRootAndChildModules(@TempDir Path workspace) throws Exception {
        Files.writeString(
                workspace.resolve("pom.xml"),
                """
                <project>
                  <artifactId>parent</artifactId>
                  <modules>
                    <module>child</module>
                  </modules>
                  <dependencies>
                    <dependency><groupId>com.anvil</groupId><artifactId>anvil-core</artifactId></dependency>
                  </dependencies>
                </project>
                """);
        Files.createDirectories(workspace.resolve("child"));
        Files.writeString(workspace.resolve("child/pom.xml"), "<project><artifactId>child</artifactId></project>");

        String formatted = MavenModuleGraph.format(workspace);
        assertTrue(formatted.contains("<module_graph>"));
        assertTrue(formatted.contains("parent"));
        assertTrue(formatted.contains("child"));
        assertTrue(formatted.contains("anvil-core"));
    }
}
