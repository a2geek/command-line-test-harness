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

import org.junit.jupiter.api.Test;
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
    public void testMatch_trim() {
        // Trim
        assertTrue(Config.MatchType.trim.matches("right", "right  "));
        assertTrue(Config.MatchType.trim.matches("right", "  right"));
        assertFalse(Config.MatchType.trim.matches("right", "wrong"));
        // Trim (multi-line)
        final var actual = " TEXT LINE 1     \nTEXT LINE 2    \nEND  ";
        final var expected = "TEXT LINE 1\nTEXT LINE 2\nEND";
        assertTrue(Config.MatchType.trim.matches(expected, actual));
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
    public void testMatch_whitespace() {
        // Test independent function
        assertEquals("An Apple A Day", Config.MatchType.whitespaceTrim("\tAn  Apple    A Day"));
        //
        assertTrue(Config.MatchType.whitespace.matches("An Apple\tA Day", "\tAn  Apple    A Day"));
        assertFalse(Config.MatchType.whitespace.matches("An Apple\tA Day", "\tA  Banana    A Day"));
        // Multiline
        final var expected = "An Apple A Day";
        final var actual = """
                \tAn\t\t\tApple
                A                 Day
                """;
        assertTrue(Config.MatchType.whitespace.matches(expected, actual));
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
}
