package io.github.a2geek.clth;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "clth", mixinStandardHelpOptions = true, description = "Command Line Test Harness")
public class Main implements Callable<Integer> {
    public static void main(String... args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Parameters(arity = "1..*", description = "Test file definitions")
    private List<Path> testFiles;

    @Override
    public Integer call() throws Exception {
        for (Path testFile : testFiles) {
            Config config = Config.load(Files.readString(testFile));
            Utility.buildTestCases(config).forEach(this::runTest);
        }
        return 0;
    }

    public void runTest(Utility.SingleTestCase testCase) {
        for (Config.Step step : testCase.steps()) {
            try {
                String[] parts = step.command().split(" ");
                // Handle command
                if (testCase.commands().containsKey(parts[0])) {
                    Config.Command command = testCase.commands().get(parts[0]);
                    Path path = Path.of(command.executable());
                    String glob = String.format("glob:%s", path.getFileName());
                    PathMatcher matcher = FileSystems.getDefault().getPathMatcher(glob);
                    Path exe = Files.find(path.getParent(), 1,
                                    (file, attr) -> matcher.matches(file.getFileName()))
                            .findFirst().orElseThrow();

                    List<String> parameters = new ArrayList<>();
                    for (int i = 1; i < parts.length; i++) {
                        if (parts[i].startsWith("$")) {
                            String varname = parts[i].substring(1);
                            if (!testCase.variables().containsKey(varname)) {
                                String msg = String.format("Found variable named '%s' but no value", varname);
                                throw new RuntimeException(msg);
                            }
                            parameters.add(testCase.variables().get(varname));
                        } else {
                            parameters.add(parts[i]);
                        }
                    }
                    // Trim out any blank parameters at end
                    while (parameters.getLast().isBlank()) {
                        parameters.removeLast();
                    }
                    parameters.addFirst(exe.toString());
                    System.out.printf("Command = %s\n", String.join(" ", parameters));

                    ProcessBuilder builder = new ProcessBuilder(parameters);

                    // Execute
                    Process process = builder.start();

                    // Handle stdin
                    if (step.stdin() != null) {
                        String input = step.stdin();
                        if (input.startsWith("file:")) {
                            input = Files.readString(Path.of(input.substring(5)));
                        }
                        try (Writer stdin = process.outputWriter()) {
                            stdin.write(input);
                        }
                    }

                    List<String> errors = new ArrayList<>();
                    if (process.waitFor() != step.returnCode()) {
                        errors.add(String.format("Expecting exit code of %d but got %d", step.returnCode(), process.exitValue()));
                    }

                    // Capture stdout & stderr
                    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
                    process.getInputStream().transferTo(stdout);
                    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
                    process.getErrorStream().transferTo(stderr);

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
                } else {
                    String msg = String.format("Expecting command named '%s' but it does not exist", parts[0]);
                    throw new RuntimeException(msg);
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
