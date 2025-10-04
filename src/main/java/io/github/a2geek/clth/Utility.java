package io.github.a2geek.clth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class Utility {
    public static Stream<SingleTestCase> buildTestCases(Config config) {
        Stream.Builder<SingleTestCase> builder = Stream.builder();
        for (Config.TestCase testCase : config.tests()) {
            // Pick the shortest length array for iterations
            int iterations = 1;
            for (Map.Entry<String, Object> entry : testCase.variables().entrySet()) {
                if (entry.getValue() instanceof List<?> list) {
                    if (iterations == 1) {
                        iterations = list.size();
                    } else {
                        iterations = Math.min(iterations, list.size());
                    }
                }
            }
            // Rebuild the variable map for every possible iteration
            for (int i = 0; i < iterations; i++) {
                Map<String, String> variables = new HashMap<>();
                final int n = i;
                testCase.variables().forEach((k, v) -> {
                    if (v instanceof List<?> list) {
                        variables.put(k, list.get(n).toString());
                    } else {
                        variables.put(k, v.toString());
                    }
                });
                builder.add(new SingleTestCase(config.commands(), testCase.name(), variables, testCase.steps()));
            }
        }
        return builder.build();
    }

    public record SingleTestCase(Map<String,Config.Command> commands,
                                 String testName,
                                 Map<String,String> variables,
                                 List<Config.Step> steps) {}
}
