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
import java.util.*;
import java.util.function.Consumer;

public class TestHarness {
    public static void run(TestSuite testSuite, TestRunner runner, Settings settings) {
        Map<String,File> testCaseFiles = new HashMap<>();
        settings.out.printf("Test '%s' %s\n", testSuite.testName(), testSuite.variables());
        for (int n=0; n<testSuite.steps().size(); n++) {
            Config.Step step = testSuite.steps().get(n);
            try {
                String[] parts = step.command().split(" ");

                if (!testSuite.commands().containsKey(parts[0])) {
                    String msg = String.format("Expecting command named '%s' but it does not exist", parts[0]);
                    throw new RuntimeException(msg);
                }
                Config.Command command = testSuite.commands().get(parts[0]);

                // Setup variables
                List<String> parameters = new ArrayList<>();
                for (int i=1; i<parts.length; i++) {
                    parameters.add(testSuite.evaluateAsArgument(parts[i], testCaseFiles));
                }
                // Apply the file preservation logic
                testCaseFiles.values().forEach(settings.filePreservation()::apply);
                // Trim out any blank parameters at end
                while (!parameters.isEmpty() && parameters.getLast().isBlank()) {
                    parameters.removeLast();
                }

                settings.out.printf("\t%d: %s %s\n", n+1, parts[0], String.join(" ", parameters));

                // Setup stdin
                InputStream stdin = InputStream.nullInputStream();
                if (step.stdin() != null) {
                    stdin = new ByteArrayInputStream(testSuite.evaluateAsBytes(step.stdin()));
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

                // Check output
                byte[] expectedStdout = testSuite.evaluateAsBytes(step.stdout());
                if (!step.match().matches(new String(expectedStdout), stdout.toString())) {
                    errors.add("STDOUT does not match");
                    String diffOut = diff(new String(expectedStdout), stdout.toString());
                    settings.out.println(diffOut.indent(10));
                }
                else if (settings.alwaysShowOutput && !stdout.toString().isBlank()) {
                    settings.out.println(stdout.toString().indent(10));
                }
                byte[] expectedStderr = testSuite.evaluateAsBytes(step.stderr());
                if (!step.match().matches(new String(expectedStderr), stderr.toString())) {
                    errors.add("STDERR does not match");
                    String diffOut = diff(new String(expectedStderr), stderr.toString());
                    settings.out.println(diffOut.indent(10));
                }
                else if (settings.alwaysShowOutput && !stderr.toString().isBlank()) {
                    settings.out.println(stderr.toString().indent(10));
                }

                if (!errors.isEmpty()) {
                    throw new RuntimeException("Errors encountered: " + errors);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public static Settings.Builder settings() {
        return new Settings.Builder();
    }
    public record Settings(FilePreservation filePreservation, PrintStream out, boolean alwaysShowOutput) {
        public static class Builder {
            private FilePreservation filePreservation = FilePreservation.DELETE;
            private PrintStream out = System.out;
            private boolean alwaysShowOutput = false;
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
            public Settings get() {
                return new Settings(filePreservation, out, alwaysShowOutput);
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
