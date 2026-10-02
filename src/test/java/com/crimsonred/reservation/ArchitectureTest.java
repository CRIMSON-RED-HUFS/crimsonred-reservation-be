package com.crimsonred.reservation;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
  @Test
  void coreDependenciesPointInward() throws Exception {
    var root = Path.of("src/main/java/com/crimsonred/reservation");
    var references =
        Pattern.compile(
            "(?:import\\s+(?:static\\s+)?|(?<![\\w.]))((?:org|jakarta|javax|com|tools)\\.[\\w.]+)");
    var corePackage =
        Pattern.compile(
            "com\\.crimsonred\\.reservation\\.(auth|member|booking|announcement|shared)\\.(domain|application)\\..+");
    int checked = 0;
    try (var files = Files.walk(root)) {
      for (var file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        var path = root.relativize(file);
        if (path.getNameCount() < 3) continue;
        String layer = path.getName(1).toString();
        if (!layer.equals("domain") && !layer.equals("application")) continue;
        checked++;
        String source = Files.readString(file).replaceFirst("^package [\\w.]+;", "");
        var matches = references.matcher(source);
        while (matches.find()) {
          String dependency = matches.group(1);
          var target = corePackage.matcher(dependency);
          assertTrue(
              target.matches()
                  && (target.group(2).equals("domain") || layer.equals("application"))
                  && (!path.getName(0).toString().equals("shared")
                      || target.group(1).equals("shared")),
              file + " depends on an outer layer: " + dependency);
        }
      }
    }
    assertTrue(checked > 0, "No domain or application sources were checked.");
  }
}
