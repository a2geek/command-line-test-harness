package io.github.a2geek.clth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public record TestSuite(Map<String, Config.Command> commands,
                        String testName,
                        Map<String, String> variables,
                        Map<String, Config.TestFile> files,
                        List<Config.Step> steps) {

    public static Stream<TestSuite> build(Config config) {
        Stream.Builder<TestSuite> builder = Stream.builder();
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
                builder.add(new TestSuite(config.commands(), testCase.name(), variables, config.files(), testCase.steps()));
            }
        }
        return builder.build();
    }
}
