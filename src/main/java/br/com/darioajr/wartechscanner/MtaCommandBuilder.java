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

import java.nio.file.Path;
import java.util.*;

public final class MtaCommandBuilder {

    private static final Map<String, List<String>> TECH_TO_TARGETS = new LinkedHashMap<>();

    /**
     * MTA --source represents the SOURCE PLATFORM being migrated FROM, not individual
     * technologies. Valid values (confirmed across MTA 6.x / 7.x):
     *   java-ee, eap6, eap7, spring, weblogic, websphere, jboss4, jboss5, camel, rhi
     *
     * These are derived from the detected technologies and used ONLY when the MTA binary
     * confirms they exist via --list-sources. Without confirmed discovery, --source is
     * omitted to avoid "unknown source" errors.
     */
    private static final Map<String, List<String>> TECH_TO_CANDIDATE_SOURCES = new LinkedHashMap<>();

    static {
        // Each tech maps to candidate MTA source platform identifiers, in priority order.
        // All Java EE / Jakarta EE technologies share the "java-ee" source.
        TECH_TO_CANDIDATE_SOURCES.put("EJB",         List.of("java-ee", "eap7", "eap6"));
        TECH_TO_CANDIDATE_SOURCES.put("JPA",         List.of("java-ee", "eap7", "eap6"));
        TECH_TO_CANDIDATE_SOURCES.put("Hibernate",   List.of("java-ee", "eap7", "eap6"));
        TECH_TO_CANDIDATE_SOURCES.put("CDI",         List.of("java-ee", "eap7", "eap6"));
        TECH_TO_CANDIDATE_SOURCES.put("JSF",         List.of("java-ee", "eap7", "eap6"));
        TECH_TO_CANDIDATE_SOURCES.put("JAX-RS",      List.of("java-ee", "eap7", "eap6"));
        TECH_TO_CANDIDATE_SOURCES.put("JAX-WS/SOAP", List.of("java-ee", "eap7", "eap6"));
        TECH_TO_CANDIDATE_SOURCES.put("Servlet",     List.of("java-ee", "eap7", "eap6"));
        TECH_TO_CANDIDATE_SOURCES.put("Spring",      List.of("spring", "spring-boot"));
        TECH_TO_CANDIDATE_SOURCES.put("Struts",      List.of("java-ee"));

        TECH_TO_TARGETS.put("EJB",         List.of("eap8", "eap7", "eap"));
        TECH_TO_TARGETS.put("JPA",         List.of("eap8", "eap7", "eap", "quarkus"));
        TECH_TO_TARGETS.put("Hibernate",   List.of("eap8", "eap7", "eap", "quarkus"));
        TECH_TO_TARGETS.put("CDI",         List.of("eap8", "eap7", "eap", "quarkus"));
        TECH_TO_TARGETS.put("JSF",         List.of("eap8", "eap7", "eap"));
        TECH_TO_TARGETS.put("JAX-RS",      List.of("eap8", "eap7", "eap", "quarkus"));
        TECH_TO_TARGETS.put("JAX-WS/SOAP", List.of("eap8", "eap7", "eap"));
        TECH_TO_TARGETS.put("Servlet",     List.of("eap8", "eap7", "eap", "cloud-readiness"));
        TECH_TO_TARGETS.put("Spring",      List.of("eap8", "eap7", "eap", "quarkus", "cloud-readiness"));
        TECH_TO_TARGETS.put("Struts",      List.of("eap8", "eap7", "eap"));
    }

    private MtaCommandBuilder() {}

    public static List<MtaSuggestion> buildAll(ScanResult result,
                                                MigrationTarget migrationTarget,
                                                MtaConfig config) {
        var suggestions = new ArrayList<MtaSuggestion>();
        var detectedNames = result.technologies.stream().map(t -> t.name).toList();
        for (var inst : config.mtaInstallations) {
            suggestions.add(buildOne(result, migrationTarget, inst, detectedNames));
        }
        return suggestions;
    }

    private static MtaSuggestion buildOne(ScanResult result,
                                           MigrationTarget migTarget,
                                           MtaInstallation inst,
                                           List<String> detectedNames) {
        var s = new MtaSuggestion();
        s.mtaLabel        = inst.label;
        s.mtaPath         = inst.type == MtaInstallationType.BARE_METAL ? inst.path : inst.image;
        s.installationType = inst.type;

        var caps          = MtaDiscovery.discover(inst);
        boolean hasCaps   = !caps.isEmpty();

        s.resolvedSources = resolveSources(detectedNames, caps, hasCaps);
        s.resolvedTargets = resolveTargets(detectedNames, migTarget, caps, hasCaps);

        s.command = switch (inst.type) {
            case BARE_METAL -> buildBareMetal(inst, result, s);
            case CONTAINER  -> buildContainer(inst, result, s, caps);
            case OPENSHIFT  -> buildOpenShift(inst, result, s);
        };

        if (caps.failureReason() != null) {
            s.note = caps.failureReason() + " — command generated from static mappings (not validated)";
        }

        return s;
    }

    // ── Command builders per type ─────────────────────────────────────────────

    private static String buildBareMetal(MtaInstallation inst, ScanResult result, MtaSuggestion s) {
        var cmd = new StringBuilder(inst.path)
                .append(" analyze")
                .append(" --input ").append(result.artifact)
                .append(" --output ./mta-report")
                .append(" --overwrite");
        appendTargetSource(cmd, s);
        return cmd.toString();
    }

    private static String buildContainer(MtaInstallation inst, ScanResult result,
                                          MtaSuggestion s, MtaDiscovery.Capabilities caps) {
        String engine   = inst.containerEngine != null ? inst.containerEngine : "docker";
        String artifact = result.artifact;
        String filename = Path.of(artifact).getFileName().toString();

        var sb = new StringBuilder();

        // registry.redhat.io requires authentication
        if (inst.image != null && inst.image.startsWith("registry.redhat.io")) {
            sb.append("# Login required: registry.redhat.io requires a Red Hat account (https://access.redhat.com)\n");
            sb.append(engine).append(" login registry.redhat.io\n");
        }

        // selinux label ":z" is harmless on non-SELinux systems
        sb.append(engine)
          .append(" run --rm")
          .append(" -v ").append(artifact).append(":/app/input/").append(filename).append(":ro,z")
          .append(" -v $(pwd)/mta-report:/app/output:z")
          .append(" ").append(inst.image)
          .append(" analyze")
          .append(" --input /app/input/").append(filename)
          .append(" --output /app/output")
                .append(" --overwrite");
        appendTargetSource(sb, s);
        for (String p : caps.providers()) sb.append(" --provider ").append(p);
        return sb.toString();
    }

    private static String buildOpenShift(MtaInstallation inst, ScanResult result, MtaSuggestion s) {
        String appName  = Path.of(result.artifact).getFileName().toString()
                .replaceAll("[^a-zA-Z0-9-]", "-").toLowerCase();
        String targets  = String.join(",", s.resolvedTargets);
        String sources  = String.join(",", s.resolvedSources);
        String ns       = inst.namespace      != null ? inst.namespace      : "mta";
        String route    = inst.hubRoute       != null ? inst.hubRoute       : "<hub-route>";
        String channel  = inst.operatorChannel != null ? inst.operatorChannel : "stable-v7";
        String catalog  = inst.operatorCatalog != null ? inst.operatorCatalog : "redhat-operators";

        // Step 1 — install operator via OLM from redhat-operators catalog
        // Step 2 — create the Analysis CR using the MTA Hub API or operator CR
        return """
                # ── 1. Install the MTA Operator (Red Hat) via OLM ─────────────────────
                # Requires login: oc login --token=<token> --server=<api-url>
                # registry.redhat.io: docker login registry.redhat.io (Red Hat account)
                oc apply -f - <<'EOF'
                apiVersion: v1
                kind: Namespace
                metadata:
                  name: %s
                ---
                apiVersion: operators.coreos.com/v1
                kind: OperatorGroup
                metadata:
                  name: mta-operatorgroup
                  namespace: %s
                spec:
                  targetNamespaces: [%s]
                ---
                apiVersion: operators.coreos.com/v1alpha1
                kind: Subscription
                metadata:
                  name: mta
                  namespace: %s
                spec:
                  channel: %s
                  installPlanApproval: Automatic
                  name: mta
                  source: %s
                  sourceNamespace: openshift-marketplace
                EOF
                \s
                # ── 2. Create the analysis via MTA Hub API ────────────────────────────
                # Wait for the operator to be Running before this step
                MTA_HUB=%s
                curl -s -X POST "$MTA_HUB/hub/applications" \\
                  -H "Content-Type: application/json" \\
                  -d '{"name":"%s","bucket":{"name":"%s"}}' | tee /tmp/mta-app.json
                \s
                APP_ID=$(jq -r '.id' /tmp/mta-app.json)
                curl -s -X POST "$MTA_HUB/hub/analyses" \\
                  -H "Content-Type: application/json" \\
                  -d '{
                    "application":{"id":'$APP_ID'},
                    "sources":[%s],
                    "targets":[%s]
                  }'""".formatted(
                ns, ns, ns, ns,
                channel, catalog,
                route,
                appName, appName,
                toJsonArray(s.resolvedSources),
                toJsonArray(s.resolvedTargets));
    }

    // ── Resolution helpers ────────────────────────────────────────────────────

    private static List<String> resolveSources(List<String> detected,
                                                MtaDiscovery.Capabilities caps, boolean hasCaps) {
        // Without confirmed discovery we cannot know which source names this MTA version
        // accepts — omitting --source is safer than generating "unknown source" errors.
        if (!hasCaps) return List.of();

        var result = new LinkedHashSet<String>();
        for (var tech : detected) {
            TECH_TO_CANDIDATE_SOURCES.getOrDefault(tech, List.of())
                    .stream()
                    .filter(caps.sources()::contains)
                    .forEach(result::add);
        }
        return new ArrayList<>(result);
    }

    private static List<String> resolveTargets(List<String> detected,
                                                MigrationTarget migTarget,
                                                MtaDiscovery.Capabilities caps, boolean hasCaps) {
        var result = new LinkedHashSet<String>();

        if (migTarget.hasEapVersion()) {
            result.add(resolveEapTarget(migTarget.eapVersion(), caps, hasCaps));
        }
        if (migTarget.hasJavaVersion()) {
            String t = resolveJavaTarget(migTarget.javaVersion(), caps, hasCaps);
            if (t != null) result.add(t);
        }

        if (result.isEmpty()) {
            for (var tech : detected) {
                var candidates = TECH_TO_TARGETS.getOrDefault(tech, List.of());
                if (hasCaps) {
                    candidates.stream().filter(caps.targets()::contains).findFirst().ifPresent(result::add);
                } else {
                    if (!candidates.isEmpty()) result.add(candidates.get(0));
                }
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * Resolves the best EAP target available in the MTA installation.
     * Priority: exact (eap81) → major (eap8) → highest available eap* target.
     * When no capabilities are known, uses major version only (eap8, not eap81).
     */
    private static String resolveEapTarget(String eapVersion, MtaDiscovery.Capabilities caps, boolean hasCaps) {
        String exact = "eap" + eapVersion.replace(".", "");
        String major = "eap" + eapVersion.replaceAll("\\..*", "");

        if (!hasCaps) return major;

        if (caps.targets().contains(exact)) return exact;
        if (caps.targets().contains(major)) return major;

        // fall back to the highest available eap* target
        return caps.targets().stream()
                .filter(t -> t.matches("eap\\d.*"))
                .max(Comparator.naturalOrder())
                .orElse(major);
    }

    /**
     * Resolves the Java target for MTA.
     * MTA 7.x uses "openjdk<N>" (e.g. openjdk21); some older versions use "java<N>".
     * Priority: openjdk<N> → java<N> → highest available openjdk* → omit if none.
     * Without capabilities, defaults to "openjdk<N>".
     */
    private static String resolveJavaTarget(int javaVersion, MtaDiscovery.Capabilities caps, boolean hasCaps) {
        String openjdk = "openjdk" + javaVersion;
        String java    = "java"    + javaVersion;
        if (!hasCaps) return openjdk;
        if (caps.targets().contains(openjdk)) return openjdk;
        if (caps.targets().contains(java))    return java;
        // fall back to highest available openjdk* target
        return caps.targets().stream()
                .filter(t -> t.matches("openjdk\\d+"))
                .max(Comparator.comparingInt(t -> Integer.parseInt(t.replaceAll("\\D", ""))))
                .orElse(null);
    }

    private static void appendTargetSource(StringBuilder cmd, MtaSuggestion s) {
        for (String t : s.resolvedTargets) cmd.append(" --target ").append(t);
        for (String src : s.resolvedSources) cmd.append(" --source ").append(src);
    }

    private static String toJsonArray(List<String> values) {
        if (values.isEmpty()) return "";
        return "\"" + String.join("\",\"", values) + "\"";
    }

}
