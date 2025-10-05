package io.github.a2geek.clth;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.*;
import java.util.stream.Stream;

public class ExecuteTests {
    @ParameterizedTest(name = "{1}: {2}")
    @MethodSource("testCases")
    public void test(TestSuite testSuite, String name, String parameters) throws Exception {
        TestHarness.run(testSuite, JUnitHelper::execute, TestHarness.FilePreservation.DELETE);
    }

    public static Stream<Arguments> testCases() {
        try (InputStream inputStream = ExecuteTests.class.getResourceAsStream("/test-config.yml")) {
            assert inputStream != null;
            String document = new String(inputStream.readAllBytes());
            Config config = Config.load(document);

            return TestSuite.build(config)
                    .map(t -> Arguments.of(t, t.testName(), String.join(" ", t.variables().values())));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
