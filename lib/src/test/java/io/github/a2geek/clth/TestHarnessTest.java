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

public class TestHarnessTest {
    @Test
    public void testDiff1() {
        final var expected = """
                10  TEXT : HOME
                20  PRINT "HELLO, WORLD"
                30  END
                """;
        final var actual = """
                10 TEXT
                20 HOME
                30 PRINT "HELLO, WORLD"
                40 END
                """;

        // Note that this is really a visual test at this time
        TestHarness.diff(expected, actual);
    }

    @Test
    public void testDiff2() {
        final var expected = """
                An
                Apple
                A
                Day
                Keeps
                The
                Doctor
                Away
                """;
        final var actual = """
                A
                Banana
                A
                Day
                """;

        // Note that this is really a visual test at this time
        TestHarness.diff(expected, actual);
    }

}
