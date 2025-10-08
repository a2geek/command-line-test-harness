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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public class TestHarness {
    public static void run(TestSuite testSuite, TestRunner runner, FilePreservation filePreservation) {
        Map<String,File> testCaseFiles = new HashMap<>();
        System.out.printf("Test '%s' %s\n", testSuite.testName(), testSuite.variables());
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
                testCaseFiles.values().forEach(filePreservation::apply);
                // Trim out any blank parameters at end
                while (!parameters.isEmpty() && parameters.getLast().isBlank()) {
                    parameters.removeLast();
                }

                System.out.printf("\t%d: %s %s\n", n+1, parts[0], String.join(" ", parameters));

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
                    System.out.println(stdout);
                }
                byte[] expectedStderr = testSuite.evaluateAsBytes(step.stderr());
                if (!step.match().matches(new String(expectedStderr), stderr.toString())) {
                    errors.add("STDERR does not match");
                    System.out.println(stderr);
                }

                if (!errors.isEmpty()) {
                    throw new RuntimeException("Errors encountered: " + errors);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
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
