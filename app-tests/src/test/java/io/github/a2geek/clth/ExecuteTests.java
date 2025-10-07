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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.*;
import java.util.stream.Stream;

public class ExecuteTests {
    @ParameterizedTest(name = "{1}: {2}")
    @MethodSource("testCasesForCatFile")
    public void testCatFile(TestSuite testSuite, String name, String parameters) {
        TestHarness.run(testSuite, JUnitHelper::execute, TestHarness.FilePreservation.DELETE);
    }

    public static Stream<Arguments> testCasesForCatFile() {
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

    @ParameterizedTest(name = "{1}: {2}")
    @MethodSource("testCasesForCLTH")
    public void testCLTH(TestSuite testSuite, String name, String parameters) {
        TestHarness.run(testSuite, JUnitHelper::execute, TestHarness.FilePreservation.DELETE);
    }

    public static Stream<Arguments> testCasesForCLTH() {
        try (InputStream inputStream = ExecuteTests.class.getResourceAsStream("/clth-config.yml")) {
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
