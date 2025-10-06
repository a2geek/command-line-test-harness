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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * This is a simple "cat" clone used to validate the unit testing capability.
 */
public class CatFile {
    public static void main(String[] args) throws IOException {
        final var usage = """
                Usage: cat [ --lower | --upper ] FILE...
                Concatenate FILE(s) to standard output.
                """.trim();
        Mode mode = Mode.AS_IS;
        if (args.length == 0) {
            System.err.println(usage);
            System.exit(1);
        }
        for (var arg : args) {
            switch (arg) {
                case "--lower" -> mode = Mode.LOWERCASE;
                case "--upper" -> mode = Mode.UPPERCASE;
                case "--help" -> {
                    System.out.println(usage);
                    System.exit(0);
                }
                default -> {
                    String content = Files.readString(Path.of(arg));
                    System.out.print(mode.apply(content));
                    System.exit(0);
                }
            }
        }
    }

    private enum Mode {
        AS_IS(s -> s),
        UPPERCASE(String::toUpperCase),
        LOWERCASE(String::toLowerCase);

        private final Function<String,String> caseFn;

        Mode(Function<String,String> caseFn) {
            this.caseFn = caseFn;
        }

        public String apply(String value) {
            return caseFn.apply(value);
        }
    }
}
