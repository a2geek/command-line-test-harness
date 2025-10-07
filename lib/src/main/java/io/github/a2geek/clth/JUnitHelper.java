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

import com.ginsberg.junit.exit.SystemExitPreventedException;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static com.ginsberg.junit.exit.assertions.SystemExitAssertion.assertThatCallsSystemExit;
import static com.ginsberg.junit.exit.assertions.SystemExitAssertion.assertThatDoesNotCallSystemExit;

public class JUnitHelper {
    public static int execute(Config.Command command, List<String> parameters, InputStream stdin, OutputStream stdout, OutputStream stderr) {
        InputStream oldStdin = System.in;
        PrintStream oldStdout = System.out;
        PrintStream oldStderr = System.err;
        try {
            Class<?> clazz = Class.forName(command.mainClass());
            Method method = clazz.getMethod("main", String[].class);

            // Execute
            System.setIn(stdin);
            System.setOut(new PrintStream(stdout));
            System.setErr(new PrintStream(stderr));
            if (command.systemExit()) {
                List<Integer> rc = new ArrayList<>();
                assertThatCallsSystemExit(() -> {
                    try {
                        method.invoke(null, new Object[]{parameters.toArray(new String[0])});
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        if (e.getCause() instanceof SystemExitPreventedException sysexit) {
                            rc.add(sysexit.getStatusCode());
                            throw sysexit;
                        }
                        throw new RuntimeException(e);
                    }
                });
                if (rc.isEmpty()) {
                    throw new RuntimeException("CLI did not use System.exit");
                }
                return rc.getFirst();
            } else {
                List<Object> rc = new ArrayList<>();
                assertThatDoesNotCallSystemExit(() -> {
                    try {
                        rc.add(method.invoke(null, new Object[]{parameters.toArray(new String[0])}));
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                });
                if (rc.getFirst() instanceof Integer n) {
                    return n;
                }
                throw new RuntimeException("CLI did not return a numeric value");
            }
        } catch (ClassNotFoundException|NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        } finally {
            System.setIn(oldStdin);
            System.setOut(oldStdout);
            System.setErr(oldStderr);
        }
    }
}
