package io.github.a2geek.clth;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class TestHarness {
    public static void run(TestSuite testSuite, TestRunner runner, FilePreservation filePreservation) {
        Map<String,File> testCaseFiles = new HashMap<>();
        for (Config.Step step : testSuite.steps()) {
            try {
                String[] parts = step.command().split(" ");

                if (!testSuite.commands().containsKey(parts[0])) {
                    String msg = String.format("Expecting command named '%s' but it does not exist", parts[0]);
                    throw new RuntimeException(msg);
                }
                Config.Command command = testSuite.commands().get(parts[0]);

                // Setup variables
                List<String> parameters = new ArrayList<>();
                for (int i = 1; i < parts.length; i++) {
                    if (parts[i].startsWith("$")) {
                        String varname = parts[i].substring(1);
                        // Simple variable
                        if (testSuite.variables().containsKey(varname)) {
                            varname = testSuite.variables().get(varname);
                            if (!varname.startsWith("$")) {
                                parameters.add(varname);
                                continue;
                            }
                            varname = varname.substring(1);
                        }
                        // Generated file (which can also be specified as the variable value)
                        // Note that we reuse the same file for the test suite
                        if (testSuite.files().containsKey(varname)) {
                            File file = testCaseFiles.computeIfAbsent(varname, name -> {
                                Config.TestFile testFile = testSuite.files().get(name);
                                File temp = testFile.asFile();
                                filePreservation.apply(temp);
                                return temp;
                            });
                            parameters.add(file.getPath());
                            continue;
                        }
                        // Confusion!
                        String msg = String.format("Found variable named '%s' but no value", varname);
                        throw new RuntimeException(msg);
                    } else {
                        parameters.add(parts[i]);
                    }
                }
                // Trim out any blank parameters at end
                while (!parameters.isEmpty() && parameters.getLast().isBlank()) {
                    parameters.removeLast();
                }

                // Setup stdin
                InputStream stdin = InputStream.nullInputStream();
                if (step.stdin() != null) {
                    byte[] data = step.stdin().getBytes();
                    if (step.stdin().startsWith("file:")) {
                        data = Files.readAllBytes(Path.of(step.stdin().substring(5)));
                    }
                    else if (step.stdin().startsWith("$")) {
                        String name = step.stdin().substring(1);
                        if (testSuite.files().containsKey(name)) {
                            Config.TestFile testFile = testSuite.files().get(name);
                            data = testFile.contentAsBytes();
                        }
                        else {
                            var msg = String.format("Expecting file named '%s' but none found", name);
                            throw new RuntimeException(msg);
                        }
                    }
                    stdin = new ByteArrayInputStream(data);
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
                if (!step.stdout().equals(stdout.toString())) {
                    errors.add("STDOUT does not match");
                    System.out.println(stdout);
                }
                if (!step.stderr().equals(stderr.toString())) {
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
