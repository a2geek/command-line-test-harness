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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigTest {
    @Test
    public void testMatch_ignore() {
        assertTrue(Config.MatchType.ignore.matches("right", "wrong"));
    }

    @Test
    public void testMatch_exact() {
        assertTrue(Config.MatchType.exact.matches("right", "right"));
        assertFalse(Config.MatchType.exact.matches("right", "wrong"));
    }

    @Test
    public void testMatch_contains() {
        assertTrue(Config.MatchType.contains.matches("right", "This is the right answer"));
        assertFalse(Config.MatchType.contains.matches("right", "This is the wrong answer"));
    }

    @Test
    public void testMatch_regex() {
        assertTrue(Config.MatchType.regex.matches(".*Apple.*", "An Apple a day"));
        assertFalse(Config.MatchType.regex.matches(".*Apple.*", "A Banana a day"));
        // Multiline
        final var regex = ".*Apple.*";
        final var actual = """
                An
                Apple
                A
                Day
                """;
        assertTrue(Config.MatchType.regex.matches(regex, actual));
    }

    @Test
    public void testWhitespace_trim() {
        final Config.Whitespace whitespace = Config.Whitespace.trim;
        // Trim
        assertEquals("right", whitespace.apply("right  "));
        assertEquals("right", whitespace.apply("  right"));
        // Trim (multi-line)
        final var actual = " TEXT LINE 1     \nTEXT LINE 2    \nEND  ";
        final var expected = "TEXT LINE 1\nTEXT LINE 2\nEND";
        assertEquals(expected, whitespace.apply(actual));
    }


    @Test
    public void testWhitespace_ignore() {
        final Config.Whitespace whitespace = Config.Whitespace.ignore;
        final var expected = "An Apple A Day";
        // Ignore
        assertEquals(expected, whitespace.apply("\tAn  Apple    A Day"));
        assertEquals(expected, whitespace.apply("An Apple\tA Day"));
        // Ignore (multi-line)
        final var actual = """
                \tAn\t\t\tApple
                A                 Day
                """;
        assertEquals("An Apple\nA Day", whitespace.apply(actual));
    }

    @Test
    public void testTestFile_contentAsBytes() {
        // Text
        Config.TestFile textFile = new Config.TestFile(Config.FileType.text, "HELLO", "", "");
        assertArrayEquals("HELLO".getBytes(), textFile.contentAsBytes());
        // Binary
        var hex = """
                20 58 fc
                60
                """;
        Config.TestFile binFile = new Config.TestFile(Config.FileType.binary, hex, "", "");
        assertArrayEquals(new byte[] { 0x20, 0x58, (byte)0xfc, 0x60 }, binFile.contentAsBytes());
    }

    @Test
    public void testLoad_commandAsString() throws JsonProcessingException {
        final String document = """
            tests:
              - name: command as string
                steps:
                  - command: cmd arg1 arg2 arg3
            """;
        Config config = Config.load(document);
        assertEquals(1, config.tests().size());
        Config.TestCase testCase = config.tests().getFirst();
        assertEquals(1, testCase.steps().size());
        Config.Step step = testCase.steps().getFirst();
        assertEquals(4, step.command().size());
        List<String> command = new ArrayList<>(step.command());
        assertEquals("cmd", command.removeFirst());
        assertEquals("arg1", command.removeFirst());
        assertEquals("arg2", command.removeFirst());
        assertEquals("arg3", command.removeFirst());
    }

    @Test
    public void testLoad_commandAsArray() throws JsonProcessingException {
        final String document = """
            tests:
              - name: command as string
                steps:
                  - command:
                      - cmd
                      - argument 1
                      - argument 2
                      - argument 3
            """;
        Config config = Config.load(document);
        assertEquals(1, config.tests().size());
        Config.TestCase testCase = config.tests().getFirst();
        assertEquals(1, testCase.steps().size());
        Config.Step step = testCase.steps().getFirst();
        assertEquals(4, step.command().size());
        List<String> command = new ArrayList<>(step.command());
        assertEquals("cmd", command.removeFirst());
        assertEquals("argument 1", command.removeFirst());
        assertEquals("argument 2", command.removeFirst());
        assertEquals("argument 3", command.removeFirst());
    }
}
