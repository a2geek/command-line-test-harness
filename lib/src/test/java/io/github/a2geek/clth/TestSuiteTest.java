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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TestSuiteTest {
    private static final String yaml =
                """
                files:
                  fname:
                    type: text
                    content: HELLO WORLD
                    prefix: atest
                    suffix: .txt
                tests:
                  - name: a test
                    variables:
                      avar: avalue
                      afile: $fname
                """;
    private TestSuite testSuite;

    @BeforeEach
    public void setup() throws JsonProcessingException {
        Config config = Config.load(yaml);
        testSuite = TestSuite.build(config).findFirst().orElseThrow();
    }

    @Test
    public void testEvaluateAsArgument() {
        Map<String, File> testCaseFiles = new HashMap<>();
        assertEquals("anystring", testSuite.evaluateAsArgument("anystring", testCaseFiles));
        assertEquals("avalue", testSuite.evaluateAsArgument("$avar", testCaseFiles));
        assertTrue(testSuite.evaluateAsArgument("$afile", testCaseFiles).matches("^.*atest.*.txt$"));
    }

    @Test
    public void testEvaluateAsBytes() throws IOException {
        final TestHarness.Settings settings = TestHarness.settings().get();
        assertArrayEquals("anystring".getBytes(), testSuite.evaluateAsBytes("anystring", settings));
        assertArrayEquals("avalue".getBytes(), testSuite.evaluateAsBytes("$avar", settings));
        assertArrayEquals("HELLO WORLD".getBytes(), testSuite.evaluateAsBytes("$afile", settings));
    }
}
