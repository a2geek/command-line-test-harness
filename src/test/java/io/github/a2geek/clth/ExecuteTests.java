package io.github.a2geek.clth;

import com.ginsberg.junit.exit.SystemExitPreventedException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.ginsberg.junit.exit.assertions.SystemExitAssertion.assertThatCallsSystemExit;
import static com.ginsberg.junit.exit.assertions.SystemExitAssertion.assertThatDoesNotCallSystemExit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class ExecuteTests {
    @ParameterizedTest(name = "{1}: {2}")
    @MethodSource("testCases")
    public void test(Map<String,Config.Command> commands, String name, Map<String,String> variables, List<Config.Step> steps) throws Exception {
        for (Config.Step step : steps) {
            String[] parts = step.command().split(" ");
            // Handle command
            if (commands.containsKey(parts[0])) {
                Config.Command command = commands.get(parts[0]);
                Class<?> clazz = Class.forName(command.mainClass());
                Method method = clazz.getMethod("main", String[].class);
                List<String> parameters = new ArrayList<>();
                for (int i=1; i<parts.length; i++) {
                    if (parts[i].startsWith("$")) {
                        String varname = parts[i].substring(1);
                        if (!variables.containsKey(varname)) {
                            fail(String.format("Found variable named '%s' but no value", varname));
                        }
                        parameters.add(variables.get(varname));
                    }
                    else {
                        parameters.add(parts[i]);
                    }
                }
                // Trim out any blank parameters at end
                while (parameters.getLast().isBlank()) {
                    parameters.removeLast();
                }
                System.out.printf("Command = %s %s\n", parts[0], String.join(" ", parameters));
                // Handle stdin
                if (step.stdin() != null) {
                    if (step.stdin().startsWith("file:")) {
                        System.setIn(new FileInputStream(step.stdin().substring(5)));
                    } else {
                        System.setIn(new ByteArrayInputStream(step.stdin().getBytes()));
                    }
                } else {
                    System.setIn(InputStream.nullInputStream());
                }
                // Capture stdout & stderr
                ByteArrayOutputStream stdout = new ByteArrayOutputStream();
                System.setOut(new PrintStream(stdout));
                ByteArrayOutputStream stderr = new ByteArrayOutputStream();
                System.setErr(new PrintStream(stderr));
                // Execute
                if (command.systemExit()) {
                    assertThatCallsSystemExit(() -> {
                        try {
                            Object rc = method.invoke(null, new Object[]{parameters.toArray(new String[0])});
                            if (rc instanceof Integer n) {
                                System.exit(n);
                            }
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            if (e.getCause() instanceof SystemExitPreventedException sysexit) {
                                throw sysexit;
                            }
                            throw new RuntimeException(e);
                        }
                    }).withExitCode(step.returnCode());
                } else {
                    assertThatDoesNotCallSystemExit(() -> {
                        try {
                            method.invoke(null, new Object[]{parameters.toArray(new String[0])});
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
                // Check output
                assertEquals(step.stdout(), stdout.toString());
                assertEquals(step.stderr(), stderr.toString());
            }
            else {
                fail(String.format("Expecting command named '%s' but it does not exist", parts[0]));
            }
        }
    }

    public static Stream<Arguments> testCases() {
        try (InputStream inputStream = ExecuteTests.class.getResourceAsStream("/sample-config.yml")) {
            assert inputStream != null;
            String document = new String(inputStream.readAllBytes());
            Config config = Config.load(document);

            return Utility.buildTestCases(config)
                    .map(t -> Arguments.of(t.commands(), t.testName(), t.variables(), t.steps()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
