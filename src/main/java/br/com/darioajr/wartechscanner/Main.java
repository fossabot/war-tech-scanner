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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.Callable;

@Command(
        name = "war-tech-scanner",
        mixinStandardHelpOptions = true,
        version = "war-tech-scanner 0.1.0",
        description = "Detects EJB, JPA, Hibernate and Java EE/Jakarta EE technologies in WAR/EAR/JAR."
)
public class Main implements Callable<Integer> {

    @Parameters(index = "0", description = ".war, .ear, .jar or .rar file to analyze")
    Path artifact;

    @Option(names = {"--json"}, description = "Prints the result as JSON (disables rich UI)")
    boolean json;

    @Option(names = {"--no-nested"}, description = "Does not analyze nested JAR/WAR/EAR/RAR")
    boolean noNested;

    @Option(names = {"--max-evidence"}, description = "Max evidences per technology. Default: ${DEFAULT-VALUE}")
    int maxEvidence = 5;

    @Option(names = {"--target-eap"}, description = "Target JBoss EAP version (e.g. 7.4, 8.0)")
    String targetEap;

    @Option(names = {"--target-java"}, description = "Target Java version (e.g. 11, 17, 21)")
    int targetJava = 0;

    @Option(names = {"--mta-config"},
            description = "Path to the MTA configuration file (war-tech-scanner-config.json). "
                        + "When provided, generates mta-cli command suggestions for each configured installation.")
    Path mtaConfig;

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        var target = new MigrationTarget(targetEap, targetJava);

        if (json) {
            var result = new ArchiveScanner(!noNested).scan(artifact);
            result.technologies.sort(byScoreDesc());
            if (target.hasEapVersion() || target.hasJavaVersion()) {
                result.migrationHints.addAll(CompatibilityAdvisor.advise(result, target));
            }
            if (mtaConfig != null) {
                var config = MtaConfig.load(mtaConfig);
                result.mtaSuggestions = MtaCommandBuilder.buildAll(result, target, config);
            }
            var mapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .enable(SerializationFeature.INDENT_OUTPUT);
            System.out.println(mapper.writeValueAsString(result));
            return result.technologies.isEmpty() ? 2 : 0;
        }

        var console = new RichConsole();
        var result = new ArchiveScanner(!noNested, console).scan(artifact);
        result.technologies.sort(byScoreDesc());
        if (target.hasEapVersion() || target.hasJavaVersion()) {
            result.migrationHints.addAll(CompatibilityAdvisor.advise(result, target));
        }
        if (mtaConfig != null) {
            var config = MtaConfig.load(mtaConfig);
            result.mtaSuggestions = MtaCommandBuilder.buildAll(result, target, config);
        }
        console.printSummary(result, maxEvidence, target);
        return result.technologies.isEmpty() ? 2 : 0;
    }

    private static Comparator<DetectedTechnology> byScoreDesc() {
        return Comparator.comparingInt((DetectedTechnology t) -> t.score)
                .reversed()
                .thenComparing(t -> t.name);
    }
}
