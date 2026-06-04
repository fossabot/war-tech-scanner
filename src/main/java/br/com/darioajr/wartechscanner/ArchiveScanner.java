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

import org.objectweb.asm.ClassReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.zip.ZipFile;

public final class ArchiveScanner {
    private final boolean scanNestedArchives;
    private final ScanProgressListener listener;

    public ArchiveScanner(boolean scanNestedArchives) {
        this(scanNestedArchives, ScanProgressListener.NOOP);
    }

    public ArchiveScanner(boolean scanNestedArchives, ScanProgressListener listener) {
        this.scanNestedArchives = scanNestedArchives;
        this.listener = listener != null ? listener : ScanProgressListener.NOOP;
    }

    public ScanResult scan(Path artifact) throws IOException {
        if (!Files.isRegularFile(artifact)) {
            throw new IOException("File not found: " + artifact);
        }
        var result = new ScanResult();
        result.artifact = artifact.toAbsolutePath().toString();
        result.artifactType = extensionOf(artifact);
        var technologies = TechnologyCatalog.create();
        scanZip(artifact, "", result, technologies);
        technologies.values().stream()
                .filter(t -> !t.evidences.isEmpty())
                .forEach(result.technologies::add);
        listener.onScanComplete();
        return result;
    }

    private void scanZip(Path file, String prefix, ScanResult result, Map<String, DetectedTechnology> techs) throws IOException {
        try (var zip = new ZipFile(file.toFile());
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            var entries = Collections.list(zip.entries());
            int total = (int) entries.stream().filter(e -> !e.isDirectory()).count();
            listener.onScanStart(result.artifact, total);

            var processed = new AtomicInteger(0);
            List<Future<?>> futures = new ArrayList<>();

            for (var entry : entries) {
                if (entry.isDirectory()) continue;
                var name = prefix + entry.getName();
                inspectEntryName(name, result, techs);
                listener.onProgress(name, processed.incrementAndGet(), total);

                if (name.endsWith(".class")) {
                    var bytes = zip.getInputStream(entry).readAllBytes();
                    futures.add(executor.submit(() -> inspectClass(name, bytes, result, techs)));
                } else if (scanNestedArchives && isArchive(name)) {
                    result.libraries.add(name);
                    listener.onNestedArchive(name);
                    var bytes = zip.getInputStream(entry).readAllBytes();
                    futures.add(executor.submit(() -> {
                        try {
                            scanNestedArchive(bytes, name + "!", result, techs);
                        } catch (Exception e) {
                            result.warnings.add("Error processing nested archive " + name + ": " + e.getMessage());
                        }
                    }));
                }
            }

            for (var f : futures) {
                try { f.get(); } catch (Exception e) {
                    result.warnings.add("Error in scan task: " + e.getMessage());
                }
            }
        }
    }

    private void scanNestedArchive(byte[] bytes, String prefix, ScanResult result, Map<String, DetectedTechnology> techs) {
        try (var jis = new JarInputStream(new ByteArrayInputStream(bytes))) {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                if (entry.isDirectory()) continue;
                var name = prefix + entry.getName();
                inspectEntryName(name, result, techs);
                if (entry.getName().endsWith(".class")) {
                    inspectClass(name, jis.readAllBytes(), result, techs);
                } else if (scanNestedArchives && isArchive(entry.getName())) {
                    result.libraries.add(name);
                    scanNestedArchive(jis.readAllBytes(), name + "!", result, techs);
                }
            }
        } catch (Exception e) {
            result.warnings.add("Could not read nested archive " + prefix + ": " + e.getMessage());
        }
    }

    private void inspectEntryName(String name, ScanResult result, Map<String, DetectedTechnology> techs) {
        var lower = name.toLowerCase(Locale.ROOT);
        var techByPath = TechnologyCatalog.techByPath(lower);
        if (techByPath != null) {
            result.descriptors.add(name);
            techs.get(techByPath).addEvidence("descriptor/resource: " + name, 10);
        }
        if (lower.endsWith(".jar")) {
            result.libraries.add(name);
            var techByLib = TechnologyCatalog.techByLibrary(lower);
            if (techByLib != null) {
                techs.get(techByLib).addEvidence("library: " + name, 5);
            }
        }
    }

    private void inspectClass(String entryName, byte[] bytes, ScanResult result, Map<String, DetectedTechnology> techs) {
        try {
            var reader = new ClassReader(bytes);
            var visitor = new ClassTechnologyVisitor();
            reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (!visitor.technologies().isEmpty()) {
                result.classesWithEvidence.add(entryName + " -> " + visitor.technologies());
                for (var tech : visitor.technologies()) {
                    techs.get(tech).addEvidence("class annotation/type: " + visitor.className() + " in " + entryName, 7);
                }
            }
        } catch (Exception e) {
            result.warnings.add("Could not analyze class " + entryName + ": " + e.getMessage());
        }
    }

    private static boolean isArchive(String name) {
        var n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".jar") || n.endsWith(".war") || n.endsWith(".ear") || n.endsWith(".rar");
    }

    private static String extensionOf(Path p) {
        var n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        var idx = n.lastIndexOf('.');
        return idx >= 0 ? n.substring(idx + 1).toUpperCase(Locale.ROOT) : "UNKNOWN";
    }
}
