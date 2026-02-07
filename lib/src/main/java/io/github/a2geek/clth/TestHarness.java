/*
 * Command Line Test Harness
 * Copyright (C) 2025  Robert Greene
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.a2geek.clth;

import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public class TestHarness {
    public static void run(TestSuite testSuite, TestRunner runner, Settings settings) {
        Map<String,File> testCaseFiles = new HashMap<>();
        settings.out.printf("Test '%s' %s\n", testSuite.testName(), testSuite.variables());
        for (int n=0; n<testSuite.steps().size(); n++) {
            Config.Step step = testSuite.steps().get(n);
            try {
                final String cmd = step.command().getFirst();
                if (!testSuite.commands().containsKey(cmd)) {
                    String msg = String.format("Expecting command named '%s' but it does not exist", cmd);
                    throw new RuntimeException(msg);
                }
                Config.Command command = testSuite.commands().get(cmd);

                // Setup variables
                List<String> parameters = new ArrayList<>();
                for (int i=1; i<step.command().size(); i++) {
                    parameters.add(testSuite.evaluateAsArgument(step.command().get(i), testCaseFiles));
                }
                // Apply the file preservation logic
                testCaseFiles.values().forEach(settings.filePreservation()::apply);
                // Trim out any blank parameters at end
                while (!parameters.isEmpty() && parameters.getLast().isBlank()) {
                    parameters.removeLast();
                }

                settings.out.printf("\t%d: %s %s\n", n+1, cmd, String.join(" ", parameters));

                // Setup stdin
                InputStream stdin = InputStream.nullInputStream();
                if (step.stdin() != null) {
                    stdin = new ByteArrayInputStream(testSuite.evaluateAsBytes(step.stdin(), settings));
                }

                // Setup stdout & stderr
                ByteArrayOutputStream stdout = new ByteArrayOutputStream();
                ByteArrayOutputStream stderr = new ByteArrayOutputStream();

                // Run step
                int rc = runner.execute(command, parameters, stdin, stdout, stderr);

                List<String> errors = new ArrayList<>();
                if (rc != step.returnCode()) {
                    errors.add(String.format("Expecting exit code of %d but got %d", step.returnCode(), rc));
                }

                // Check stdout
                if (step.stdout() != null) {
                    byte[] expectedStdout = testSuite.evaluateAsBytes(step.stdout(), settings);
                    handleOutput("stdout", step, settings, new String(expectedStdout), stdout.toString(), errors);
                }

                // Check stderr
                if (step.stderr() != null) {
                    byte[] expectedStderr = testSuite.evaluateAsBytes(step.stderr(), settings);
                    handleOutput("stderr", step, settings, new String(expectedStderr), stderr.toString(), errors);
                }

                if (!errors.isEmpty()) {
                    throw new RuntimeException("Errors encountered: " + errors);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
    public static void handleOutput(String name, Config.Step step, Settings settings,
                                    String expected, String actual, List<String> errors) {
        Config.Whitespace whitespace = step.criteria().whitespace();
        expected = whitespace.apply(expected);
        actual = whitespace.apply(actual);

        Config.MatchType matchType = step.criteria().match();
        if (!matchType.matches(expected, actual)) {
            errors.add(String.format("'%s' does not match", name));
            String diffOut = diff(expected, actual);
            settings.out.println(diffOut.indent(10));
        }
        else if (settings.alwaysShowOutput && !actual.isBlank()) {
            settings.out.println(actual.indent(10));
        }
    }

    public static Settings.Builder settings() {
        return new Settings.Builder();
    }
    public record Settings(FilePreservation filePreservation, PrintStream out, boolean alwaysShowOutput, Path baseDirectory) {
        public static class Builder {
            private FilePreservation filePreservation = FilePreservation.DELETE;
            private PrintStream out = System.out;
            private boolean alwaysShowOutput = false;
            private Path baseDirectory = Path.of(System.getProperty("user.dir"));   // default to working directory
            public Builder deleteFiles() {
                this.filePreservation = FilePreservation.DELETE;
                return this;
            }
            public Builder keepFiles() {
                this.filePreservation = FilePreservation.KEEP;
                return this;
            }
            public Builder out(PrintStream out) {
                assert out != null;
                this.out = out;
                return this;
            }
            public Builder enableAlwaysShowOutput() {
                this.alwaysShowOutput = true;
                return this;
            }
            public Builder baseDirectory(Path baseDirectory) {
                this.baseDirectory = baseDirectory;
                return this;
            }
            public Settings get() {
                return new Settings(filePreservation, out, alwaysShowOutput, baseDirectory);
            }
        }
    }

    public static String diff(String expected, String actual) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        final Map<DiffRow.Tag,String> tags = Map.of(
                DiffRow.Tag.EQUAL, "=",
                DiffRow.Tag.CHANGE, "!",
                DiffRow.Tag.DELETE, "-",
                DiffRow.Tag.INSERT, "+"
            );
        DiffRowGenerator generator = DiffRowGenerator.create().build();
        List<DiffRow> rows = generator.generateDiffRows(expected.lines().toList(), actual.lines().toList());
        int oldSize = rows.stream().mapToInt(r -> r.getOldLine().length()).max().orElse(0);
        int newSize = rows.stream().mapToInt(r -> r.getNewLine().length()).max().orElse(0);
        // Note we assume only one of these might be 0
        if (oldSize == 0) {
            final String fmt = String.format("%%s |          | %%-%1$d.%1$ds |\n", newSize);
            rows.forEach(diff -> pw.printf(fmt, tags.get(diff.getTag()), diff.getNewLine()));
        }
        else if (newSize == 0) {
            final String fmt = String.format("%%s | %%-%1$d.%1$ds |          |\n", oldSize);
            rows.forEach(diff -> pw.printf(fmt, tags.get(diff.getTag()), diff.getOldLine()));
        }
        else {
            final String fmt = String.format("%%s | %%-%1$d.%1$ds | %%-%2$d.%2$ds |\n", oldSize, newSize);
            rows.forEach(diff -> pw.printf(fmt, tags.get(diff.getTag()), diff.getOldLine(), diff.getNewLine()));
        }
        return sw.toString();
    }

    public enum FilePreservation {
        DELETE(File::deleteOnExit),
        KEEP(file -> {});

        private final Consumer<File> preservationFn;

        FilePreservation(Consumer<File> preservationFn) {
            this.preservationFn = preservationFn;
        }
        public void apply(File file) {
            preservationFn.accept(file);
        }
    }

    public interface TestRunner {
        int execute(Config.Command command, List<String> parameters, InputStream stdin, OutputStream stdout, OutputStream stderr);
    }
}
