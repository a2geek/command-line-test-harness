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

                System.out.printf("\t%d: %s %s\n", n+1, parts[0], String.join(" ", parameters));

                // Setup stdin
                InputStream stdin = InputStream.nullInputStream();
                if (step.stdin() != null) {
                    stdin = new ByteArrayInputStream(evaluateAsBytes(step.stdin(), testSuite.files()));
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
                byte[] expectedStdout = evaluateAsBytes(step.stdout(), testSuite.files());
                if (!step.match().matches(new String(expectedStdout), stdout.toString())) {
                    errors.add("STDOUT does not match");
                    System.out.println(stdout);
                }
                byte[] expectedStderr = evaluateAsBytes(step.stderr(), testSuite.files());
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

    private static byte[] evaluateAsBytes(String varname, Map<String,Config.TestFile> files) throws IOException {
        byte[] data = varname.getBytes();
        if (varname.startsWith("file:")) {
            data = Files.readAllBytes(Path.of(varname.substring(5)));
        }
        else if (varname.startsWith("$")) {
            String name = varname.substring(1);
            if (files.containsKey(name)) {
                Config.TestFile testFile = files.get(name);
                data = testFile.contentAsBytes();
            }
            else {
                var msg = String.format("Expecting file named '%s' but none found", name);
                throw new RuntimeException(msg);
            }
        }
        return data;
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
