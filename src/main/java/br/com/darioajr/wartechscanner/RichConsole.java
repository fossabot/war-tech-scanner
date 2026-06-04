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

import org.fusesource.jansi.Ansi.Color;
import org.fusesource.jansi.AnsiConsole;

import java.util.List;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * Terminal UI: animated spinner, progress bar, bar chart and box layout.
 *
 * Supports three rendering modes chosen at construction time:
 *   RICH    – ANSI colors + Unicode box/bar/spinner chars (Mac, Linux, Windows Terminal)
 *   BASIC   – ANSI colors + ASCII-safe chars (cmd.exe / PowerShell without Unicode font)
 *   PLAIN   – no ANSI, no Unicode (piped output, CI)
 */
public final class RichConsole implements ScanProgressListener {

    // ── rendering mode ────────────────────────────────────────────────────────
    private enum Mode { RICH, BASIC, PLAIN }

    // ── character sets ────────────────────────────────────────────────────────
    private record Chars(
            String[] spinner,
            String barFilled, String barEmpty,
            String boxH,  String boxV,
            String boxTL, String boxTR, String boxBL, String boxBR,
            String boxML, String boxMR,
            String bullet, String nested, String more, String warn, String hint
    ) {
        static Chars unicode() {
            return new Chars(
                    new String[]{"⠋","⠙","⠹","⠸","⠼","⠴","⠦","⠧","⠇","⠏"},
                    "█", "░",
                    "═", "║", "╔", "╗", "╚", "╝", "╠", "╣",
                    "·", "↳", "…", "⚠", "»"
            );
        }

        static Chars ascii() {
            return new Chars(
                    new String[]{"|", "/", "-", "\\"},
                    "#", ".",
                    "-", "|", "+", "+", "+", "+", "+", "+",
                    "*", ">", "...", "!", ">>"
            );
        }
    }

    private static final int PROGRESS_BAR_WIDTH = 32;

    // ── terminal capabilities ─────────────────────────────────────────────────
    private final Mode mode;
    private final Chars ch;
    private final int termWidth;

    // ── scan state (volatile: written by scan thread, read by render thread) ──
    private volatile int     processed;
    private volatile int     total;
    private volatile String  currentFile = "";
    private volatile boolean running;
    private Thread spinnerThread;

    public RichConsole() {
        boolean hasAnsi    = detectAnsiSupport();
        boolean hasUnicode = detectUnicodeSupport();

        if (hasAnsi && hasUnicode) {
            mode = Mode.RICH;
            ch   = Chars.unicode();
            AnsiConsole.systemInstall();
        } else if (hasAnsi) {
            mode = Mode.BASIC;
            ch   = Chars.ascii();
            AnsiConsole.systemInstall();
        } else {
            mode = Mode.PLAIN;
            ch   = Chars.ascii();
        }

        termWidth = resolveTermWidth();
    }

    // ── ScanProgressListener ─────────────────────────────────────────────────

    @Override
    public void onScanStart(String artifact, int totalEntries) {
        this.total     = totalEntries;
        this.processed = 0;
        this.running   = true;

        if (mode == Mode.PLAIN) {
            System.out.println("Scanning: " + artifact + " (" + totalEntries + " entries)");
            return;
        }

        printBanner();
        System.out.println(ansi().fgBrightBlack().a("  File    : ").reset().fgCyan().a(artifact).reset());
        System.out.println(ansi().fgBrightBlack().a("  Entries : ").reset().a(totalEntries));
        System.out.println();
        spinnerThread = Thread.ofVirtual().start(this::renderLoop);
    }

    @Override
    public void onProgress(String currentEntry, int proc, int tot) {
        this.processed   = proc;
        this.total       = tot;
        this.currentFile = shortName(currentEntry);
    }

    @Override
    public void onNestedArchive(String name) {
        this.currentFile = ch.nested() + " " + shortName(name);
    }

    @Override
    public void onScanComplete() {
        this.running = false;
        if (spinnerThread != null) {
            try { spinnerThread.join(600); } catch (InterruptedException ignored) {}
        }
        if (mode != Mode.PLAIN) clearLine();
    }

    // ── Public rendering ──────────────────────────────────────────────────────

    public void printSummary(ScanResult result, int maxEvidence, MigrationTarget target) {
        if (mode == Mode.PLAIN) {
            plainSummary(result, maxEvidence, target);
            return;
        }

        int boxWidth = Math.min(termWidth - 2, 90);

        printBoxTop(boxWidth);
        printBoxTitle("WAR TECH SCANNER  -  RESULT", boxWidth, Color.CYAN);
        printBoxSep(boxWidth);
        printBoxLine("  File     :  " + result.artifact, boxWidth);
        printBoxLine("  Type     :  " + result.artifactType, boxWidth);
        printBoxLine("  Scan     :  " + result.scannedAt, boxWidth);
        printBoxLine("  Classes  :  " + result.classesWithEvidence.size()
                + "   Descriptors: " + result.descriptors.size()
                + "   Libs: " + result.libraries.size(), boxWidth);

        if (result.technologies.isEmpty()) {
            printBoxSep(boxWidth);
            printBoxLine(ansi().fgYellow().a("  No known technology detected.").reset().toString(), boxWidth);
            printBoxBottom(boxWidth);
            cleanup();
            return;
        }

        // ── bar chart ────────────────────────────────────────────────────────
        printBoxSep(boxWidth);
        printBoxTitle("DETECTED TECHNOLOGIES", boxWidth, Color.WHITE);
        printBoxSep(boxWidth);

        int maxScore   = result.technologies.stream().mapToInt(t -> t.score).max().orElse(1);
        int chartWidth = boxWidth - 26;

        for (var tech : result.technologies) {
            int filled   = Math.max(1, tech.score * chartWidth / Math.max(maxScore, 1));
            int empty    = chartWidth - filled;
            Color color  = scoreColor(tech.score, maxScore);

            String bar   = ansi().fg(color).a(ch.barFilled().repeat(filled)).reset()
                               .fgBrightBlack().a(ch.barEmpty().repeat(empty)).reset().toString();
            String score = ansi().fg(color).bold().a("%4d pts".formatted(tech.score)).reset().toString();
            String label = "%-12s".formatted(tech.name);
            printBoxLine("  " + ansi().bold().a(label).reset() + " " + bar + " " + score, boxWidth);

            int shown = 0;
            for (var ev : tech.evidences) {
                if (shown++ >= maxEvidence) {
                    int remaining = tech.evidences.size() - maxEvidence;
                    printBoxLine(ansi().fgBrightBlack()
                            .a("    " + ch.more() + " +" + remaining + " more").reset().toString(), boxWidth);
                    break;
                }
                printBoxLine(ansi().fgBrightBlack()
                        .a("    " + ch.bullet() + " " + truncate(ev, boxWidth - 12)).reset().toString(), boxWidth);
            }
        }

        // ── warnings ─────────────────────────────────────────────────────────
        if (!result.warnings.isEmpty()) {
            printBoxSep(boxWidth);
            printBoxTitle("WARNINGS", boxWidth, Color.YELLOW);
            printBoxSep(boxWidth);
            for (var w : result.warnings) {
                printBoxLine(ansi().fgYellow().a("  " + ch.warn() + "  ").reset()
                        .a(truncate(w, boxWidth - 8)).toString(), boxWidth);
            }
        }

        // ── migration hints ──────────────────────────────────────────────────
        if (!result.migrationHints.isEmpty()) {
            String title = "MIGRATION";
            if (target.hasEapVersion()) title += " -> EAP " + target.eapVersion();
            if (target.hasJavaVersion()) title += " / Java " + target.javaVersion();
            printBoxSep(boxWidth);
            printBoxTitle(title, boxWidth, Color.MAGENTA);
            printBoxSep(boxWidth);
            for (var hint : result.migrationHints) {
                printBoxLine(ansi().fgMagenta().a("  " + ch.hint() + "  ").reset()
                        .a(truncate(hint, boxWidth - 8)).toString(), boxWidth);
            }
        }

        // ── MTA command suggestions (one block per installation) ─────────────
        if (result.mtaSuggestions != null && !result.mtaSuggestions.isEmpty()) {
            for (var s : result.mtaSuggestions) {
                printBoxSep(boxWidth);
                String typeTag = s.installationType != null ? " [" + s.installationType + "]" : "";
                printBoxTitle("MTA: " + s.mtaLabel + typeTag, boxWidth, Color.GREEN);
                printBoxSep(boxWidth);
                if (s.note != null) {
                    printBoxLine(ansi().fgYellow().a("  " + ch.warn() + "  ").reset()
                            .a(truncate(s.note, boxWidth - 8)).toString(), boxWidth);
                }
                if (!s.resolvedTargets.isEmpty()) {
                    printBoxLine(ansi().fgBrightBlack().a("  targets : ").reset()
                            .a(String.join(", ", s.resolvedTargets)).toString(), boxWidth);
                }
                if (!s.resolvedSources.isEmpty()) {
                    printBoxLine(ansi().fgBrightBlack().a("  sources : ").reset()
                            .a(String.join(", ", s.resolvedSources)).toString(), boxWidth);
                }
                // OPENSHIFT uses multiline YAML — print each line separately
                for (String cmdLine : s.command.split("\n")) {
                    printBoxLine(ansi().fgGreen().a("  " + ch.hint() + "  ").reset()
                            .a(truncate(cmdLine, boxWidth - 8)).toString(), boxWidth);
                }
            }
        }

        printBoxBottom(boxWidth);
        cleanup();
    }

    // ── Spinner render loop ───────────────────────────────────────────────────

    private void renderLoop() {
        int frame = 0;
        while (running) {
            renderProgressLine(ch.spinner()[frame % ch.spinner().length]);
            frame++;
            try { Thread.sleep(80); } catch (InterruptedException e) { break; }
        }
        clearLine();
    }

    private void renderProgressLine(String frame) {
        int    proc = processed;
        int    tot  = total;
        int    pct  = tot > 0 ? proc * 100 / tot : 0;
        String bar  = buildBar(proc, tot, PROGRESS_BAR_WIDTH);
        String file = truncate(currentFile, termWidth - PROGRESS_BAR_WIDTH - 22);

        System.out.print(ansi()
                .cursorToColumn(1).eraseLine()
                .fgCyan().bold().a(frame).reset().a(" ")
                .a("[").fgGreen().a(bar).reset().a("] ")
                .fgBrightYellow().bold().a("%3d%%".formatted(pct)).reset()
                .fgBrightBlack().a(" (%d/%d) ".formatted(proc, tot)).reset()
                .fgDefault().a(file));
        System.out.flush();
    }

    // ── Box drawing ───────────────────────────────────────────────────────────

    private void printBanner() {
        if (mode == Mode.RICH) {
            System.out.println(ansi().bold().fgCyan().a("""
                    ██╗    ██╗ █████╗ ██████╗    ████████╗███████╗ ██████╗██╗  ██╗
                    ██║    ██║██╔══██╗██╔══██╗   ╚══██╔══╝██╔════╝██╔════╝██║  ██║
                    ██║ █╗ ██║███████║██████╔╝      ██║   █████╗  ██║     ███████║
                    ██║███╗██║██╔══██║██╔══██╗      ██║   ██╔══╝  ██║     ██╔══██║
                    ╚███╔███╔╝██║  ██║██║  ██║      ██║   ███████╗╚██████╗██║  ██║
                     ╚══╝╚══╝ ╚═╝  ╚═╝╚═╝  ╚═╝      ╚═╝   ╚══════╝ ╚═════╝╚═╝  ╚═╝
                    """).reset());
        } else {
            System.out.println(ansi().bold().fgCyan()
                    .a("=== WAR TECH SCANNER ===").reset());
        }
        System.out.println(ansi().fgBrightBlack()
                .a("  Scanner de Tecnologias Java EE / Jakarta EE").reset());
        System.out.println();
    }

    private void printBoxTop(int w) {
        System.out.println(ansi().fgBrightBlack()
                .a(ch.boxTL() + ch.boxH().repeat(w - 2) + ch.boxTR()).reset());
    }

    private void printBoxBottom(int w) {
        System.out.println(ansi().fgBrightBlack()
                .a(ch.boxBL() + ch.boxH().repeat(w - 2) + ch.boxBR()).reset());
    }

    private void printBoxSep(int w) {
        System.out.println(ansi().fgBrightBlack()
                .a(ch.boxML() + ch.boxH().repeat(w - 2) + ch.boxMR()).reset());
    }

    private void printBoxTitle(String title, int w, Color color) {
        int pad   = Math.max(0, w - 4 - visibleLength(title));
        int left  = pad / 2;
        int right = pad - left;
        System.out.println(ansi()
                .fgBrightBlack().a(ch.boxV() + " ").reset()
                .a(" ".repeat(left))
                .fg(color).bold().a(title).reset()
                .a(" ".repeat(right))
                .fgBrightBlack().a(" " + ch.boxV()).reset());
    }

    private void printBoxLine(String content, int w) {
        int pad = Math.max(0, w - 4 - visibleLength(content));
        System.out.println(ansi()
                .fgBrightBlack().a(ch.boxV() + " ").reset()
                .a(content)
                .a(" ".repeat(pad))
                .fgBrightBlack().a(" " + ch.boxV()).reset());
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private String buildBar(int value, int max, int width) {
        if (max == 0) return ch.barEmpty().repeat(width);
        int filled = Math.min(width, value * width / max);
        return ch.barFilled().repeat(filled) + ch.barEmpty().repeat(width - filled);
    }

    private static Color scoreColor(int score, int maxScore) {
        if (maxScore == 0) return Color.GREEN;
        double ratio = (double) score / maxScore;
        if (ratio >= 0.6) return Color.RED;
        if (ratio >= 0.25) return Color.YELLOW;
        return Color.GREEN;
    }

    /** Strips ANSI escape sequences to compute printable character count. */
    private static int visibleLength(String s) {
        return s.replaceAll("\\[[;\\d]*m", "").length();
    }

    private static String shortName(String path) {
        if (path == null) return "";
        // ZIP entries always use '/' as separator
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    private String truncate(String s, int max) {
        if (s == null || max <= 3) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + ch.more();
    }

    private void clearLine() {
        System.out.print(ansi().cursorToColumn(1).eraseLine());
        System.out.flush();
    }

    private void cleanup() {
        if (mode != Mode.PLAIN) AnsiConsole.systemUninstall();
    }

    // ── Capability detection ─────────────────────────────────────────────────

    private static boolean detectAnsiSupport() {
        // Piped output: no ANSI
        if (System.console() == null) return false;
        // Explicit disable flags used by many CI systems
        String noColor = System.getenv("NO_COLOR");
        if (noColor != null) return false;
        String term = System.getenv("TERM");
        if ("dumb".equalsIgnoreCase(term)) return false;
        return true;
    }

    private static boolean detectUnicodeSupport() {
        String os = System.getProperty("os.name", "").toLowerCase();
        // Mac / Linux: assume yes unless TERM=dumb
        if (!os.contains("win")) {
            String term = System.getenv("TERM");
            return !"dumb".equalsIgnoreCase(term);
        }
        // Windows Terminal (modern, full Unicode)
        if (System.getenv("WT_SESSION") != null) return true;
        // ConEmu / cmder
        if (System.getenv("ConEmuPID") != null) return true;
        // MSYS2 / Git Bash / Cygwin export TERM
        String term = System.getenv("TERM");
        if (term != null && !term.isBlank()) return true;
        // VS Code integrated terminal
        if ("vscode".equalsIgnoreCase(System.getenv("TERM_PROGRAM"))) return true;
        // Check if JVM stdout is already UTF-8
        return java.nio.charset.Charset.defaultCharset()
                .equals(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static int resolveTermWidth() {
        // Standard env var respected by most terminals
        try {
            String cols = System.getenv("COLUMNS");
            if (cols != null) return Integer.parseInt(cols.trim());
        } catch (NumberFormatException ignored) {}
        return 100;
    }

    // ── Plain-text fallback ───────────────────────────────────────────────────

    private void plainSummary(ScanResult result, int maxEvidence, MigrationTarget target) {
        System.out.println("Artifact : " + result.artifact);
        System.out.println("Type     : " + result.artifactType);
        System.out.println("Scanned  : " + result.scannedAt);
        System.out.println();
        if (result.technologies.isEmpty()) {
            System.out.println("No known technologies detected.");
        } else {
            System.out.println("Detected technologies:");
            for (var tech : result.technologies) {
                System.out.printf("- %-14s score=%-5d evidences=%d%n",
                        tech.name, tech.score, tech.evidences.size());
                int c = 0;
                for (var ev : tech.evidences) {
                    if (c++ >= maxEvidence) { System.out.println("  ..."); break; }
                    System.out.println("  * " + ev);
                }
            }
        }
        if (!result.warnings.isEmpty()) {
            System.out.println("\nWarnings:");
            result.warnings.forEach(w -> System.out.println("  ! " + w));
        }
        if (!result.migrationHints.isEmpty()) {
            System.out.println("\nMigration analysis:");
            result.migrationHints.forEach(h -> System.out.println("  >> " + h));
        }
        if (result.mtaSuggestions != null && !result.mtaSuggestions.isEmpty()) {
            for (var s : result.mtaSuggestions) {
                String typeTag = s.installationType != null ? " [" + s.installationType + "]" : "";
                System.out.println("\nMTA command [" + s.mtaLabel + typeTag + "]:");
                if (s.note != null)               System.out.println("  ! " + s.note);
                if (!s.resolvedTargets.isEmpty()) System.out.println("  targets : " + String.join(", ", s.resolvedTargets));
                if (!s.resolvedSources.isEmpty()) System.out.println("  sources : " + String.join(", ", s.resolvedSources));
                for (String line : s.command.split("\n")) System.out.println("  " + line);
            }
        }
    }
}
