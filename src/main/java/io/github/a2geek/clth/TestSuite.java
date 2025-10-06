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
