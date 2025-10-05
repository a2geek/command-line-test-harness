package io.github.a2geek.clth;

import picocli.CommandLine;
import picocli.CommandLine.*;

import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

@Command(name = "clth", mixinStandardHelpOptions = true, description = "Command Line Test Harness")
public class Main implements Callable<Integer> {
    public static void main(String... args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Parameters(arity = "1..*", description = "Test file definitions")
    private List<Path> testFiles;

    @Option(names = "--keep-files", description = "Keep all temporary test files for review")
    public void selectKeepFiles(boolean flag) {
        filePreservation = TestHarness.FilePreservation.KEEP;
    }
    @Option(names = "--delete-files", description = "Delete all temporary test files (default)")
    public void selectDeleteFiles(boolean flag) {
        filePreservation = TestHarness.FilePreservation.DELETE;
    }
    private TestHarness.FilePreservation filePreservation = TestHarness.FilePreservation.DELETE;

    @Override
    public Integer call() throws Exception {
        for (Path testFile : testFiles) {
            Config config = Config.load(Files.readString(testFile));
            TestSuite.build(config).forEach(testSuite -> TestHarness.run(testSuite, this::execute, filePreservation));
        }
        return 0;
    }

    public int execute(Config.Command command, List<String> parameters, InputStream stdin, OutputStream stdout, OutputStream stderr) {
        try {
            Path path = Path.of(command.executable());
            String glob = String.format("glob:%s", path.getFileName());
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher(glob);

            Path exe;
            try (Stream<Path> paths = Files.find(path.getParent(), 1,
                    (file, attr) -> matcher.matches(file.getFileName()))) {
                exe = paths.findFirst().orElseThrow(() -> {
                    String msg = String.format("Unable to locate executable at '%s'", command.executable());
                    return new RuntimeException(msg);
                });
            }

            parameters.addFirst(exe.toString());
            System.out.printf("Command = %s\n", String.join(" ", parameters));

            ProcessBuilder builder = new ProcessBuilder(parameters);

            // Setup
            Process process = builder.start();

            // Handle stdin
            try (OutputStream outputStream = process.getOutputStream()) {
                stdin.transferTo(outputStream);
            }

            // Wait for execution to finish
            int returnCode = process.waitFor();

            // Capture stdout & stderr
            process.getInputStream().transferTo(stdout);
            process.getErrorStream().transferTo(stderr);

            return returnCode;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
