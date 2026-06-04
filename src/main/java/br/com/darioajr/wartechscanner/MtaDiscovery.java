/*
 * Copyright 2024-present Dario Alves Junior
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package br.com.darioajr.wartechscanner;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Runs the MTA CLI (or container equivalent) to discover available sources,
 * targets and providers for a given installation.
 */
public final class MtaDiscovery {

    private MtaDiscovery() {}

    public record Capabilities(Set<String> sources, Set<String> targets, Set<String> providers,
                               String failureReason) {
        public boolean isEmpty() { return sources.isEmpty() && targets.isEmpty(); }
    }

    public static Capabilities discover(MtaInstallation installation) {
        return switch (installation.type) {
            case BARE_METAL -> discoverBareMetal(installation);
            case CONTAINER  -> discoverContainer(installation);
            case OPENSHIFT  -> emptyCapabilities("Hub API discovery not supported — using static mappings");
        };
    }

    // ── BARE_METAL ────────────────────────────────────────────────────────────

    private static Capabilities discoverBareMetal(MtaInstallation inst) {
        if (!executableExists(inst.path))
            return emptyCapabilities("Binary not found at '" + inst.path + "'");
        var sources   = runList(inst.path, "--list-sources");
        var targets   = runList(inst.path, "--list-targets");
        var providers = runList(inst.path, "--list-providers");
        if (sources.isEmpty() && targets.isEmpty())
            return emptyCapabilities("Binary found but discovery failed — check that ~/.kantra is configured: "
                    + "cp -r <mta-dir>/{rulesets,jdtls,fernflower.jar,maven.default.index,static-report} ~/.kantra/");
        return new Capabilities(sources, targets, providers, null);
    }

    // ── CONTAINER ─────────────────────────────────────────────────────────────

    private static Capabilities discoverContainer(MtaInstallation inst) {
        if (inst.image == null || inst.image.isBlank())
            return emptyCapabilities("No image configured");
        String engine = inst.containerEngine != null ? inst.containerEngine : "docker";
        var sources   = runContainerList(engine, inst.image, "--list-sources");
        var targets   = runContainerList(engine, inst.image, "--list-targets");
        var providers = runContainerList(engine, inst.image, "--list-providers");
        if (sources.isEmpty() && targets.isEmpty()) {
            String pull = inst.image.startsWith("registry.redhat.io")
                    ? engine + " login registry.redhat.io && " + engine + " pull " + inst.image
                    : engine + " pull " + inst.image;
            return emptyCapabilities("Image not available locally — run: " + pull);
        }
        return new Capabilities(sources, targets, providers, null);
    }

    private static Set<String> runContainerList(String engine, String image, String flag) {
        try {
            var proc = new ProcessBuilder(engine, "run", "--rm", image, "analyze", flag)
                    .redirectErrorStream(true)
                    .start();
            proc.waitFor(60, TimeUnit.SECONDS);
            return parseOutput(proc);
        } catch (Exception e) {
            return new LinkedHashSet<>();
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Set<String> runList(String cliPath, String flag) {
        try {
            var proc = new ProcessBuilder(cliPath, "analyze", flag)
                    .redirectErrorStream(true)
                    .start();
            proc.waitFor(30, TimeUnit.SECONDS);
            return parseOutput(proc);
        } catch (Exception e) {
            return new LinkedHashSet<>();
        }
    }

    private static Set<String> parseOutput(Process proc) throws Exception {
        var lines = new java.util.ArrayList<String>();
        try (var reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        }
        // discard all output if the process failed (e.g. image not found, binary missing)
        if (proc.exitValue() != 0) return new LinkedHashSet<>();
        var result = new LinkedHashSet<String>();
        for (var line : lines) {
            String token = parseLine(line);
            if (token != null) result.add(token);
        }
        return result;
    }

    /**
     * Parses a single line of MTA list output.
     * Typical formats:
     *   "  eap8          JBoss EAP 8"
     *   "- eap8"
     *   "eap8"
     */
    private static String parseLine(String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null;
        if (trimmed.startsWith("-") || trimmed.startsWith("*")) trimmed = trimmed.substring(1).stripLeading();
        String token = trimmed.split("\\s+")[0];
        // skip header lines (all-caps words like NAME, SOURCES, TARGETS)
        if (token.equals(token.toUpperCase()) && token.length() > 2) return null;
        // skip error/warning lines and tokens with non-identifier chars (colons, slashes, dots…)
        if (token.contains(":") || token.contains("/") || token.contains("\\")) return null;
        // valid MTA identifiers: lowercase-alphanumeric with optional hyphens
        if (!token.matches("[a-zA-Z][a-zA-Z0-9-]*")) return null;
        return token;
    }

    private static boolean executableExists(String path) {
        return path != null && Files.isExecutable(Path.of(path));
    }

    private static Capabilities emptyCapabilities(String reason) {
        return new Capabilities(new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>(), reason);
    }
}
