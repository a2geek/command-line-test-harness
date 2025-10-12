# Command Line Test Harness

[![Current Release](https://img.shields.io/github/release/a2geek/command-line-test-harness.svg)](https://github.com/a2geek/command-line-test-harness/releases)
![License](https://img.shields.io/github/license/a2geek/command-line-test-harness)

> Note: Even though the unit test capacity is targeted to Java applications, the executable component should be
> readily usable by any executable.

An experimental declarative test harness for Java applications with command-line interfaces that also build a native
executable. This project aims to allow a test to be declared, run as a normal unit test to verify functionality within
an IDE, as well as run the same set of tests from the command-line.

The specific use case for this tool is Java applications that use the Graal native-image mechanism. Due to the dynamic
nature of Java, a Graal native image doesn't always include all the components. The intent is to allow execution as a
Java application to essentially verify the tests (as well as functionality), and then re-execute the test after the
Graal native image has been produced.

## CLI

```shell
$ clth --help
Usage: clth [-hV] [--delete-files] [--keep-files] <testFiles>...
Command Line Test Harness
      <testFiles>...   Test file definitions
      --delete-files   Delete all temporary test files (default)
  -h, --help           Show this help message and exit.
      --keep-files     Keep all temporary test files for review
  -V, --version        Print version information and exit.
```

Sample successful run:

```shell
$ clth app-tests/src/test/resources/clth-config.yml 
Test 'no args' {}
	1: clth 
Test 'help flag' {}
	1: clth --help
Test 'version flag' {}
	1: clth --version
```

Sample error run:

```shell
$ clth app-tests/src/test/resources/clth-config.yml 
Test 'no args' {}
	1: clth 
Test 'help flag' {}
	1: clth --help
Test 'version flag' {}
	1: clth --version
Command Line Test Harness 'clth'
1.1-SNAPSHOT

java.lang.RuntimeException: Errors encountered: [STDOUT does not match]
	at io.github.a2geek.clth.TestHarness.run(TestHarness.java:112)
	at io.github.a2geek.clth.app.Main.lambda$call$0(Main.java:59)
	at java.base@21.0.7/java.util.stream.SpinedBuffer$1Splitr.forEachRemaining(SpinedBuffer.java:364)
	at java.base@21.0.7/java.util.stream.ReferencePipeline$Head.forEach(ReferencePipeline.java:762)
	at io.github.a2geek.clth.app.Main.call(Main.java:59)
	at io.github.a2geek.clth.app.Main.call(Main.java:35)
	at picocli.CommandLine.executeUserObject(CommandLine.java:2031)
	at picocli.CommandLine.access$1500(CommandLine.java:148)
	at picocli.CommandLine$RunLast.executeUserObjectOfLastSubcommandWithSameParent(CommandLine.java:2469)
	at picocli.CommandLine$RunLast.handle(CommandLine.java:2461)
	at picocli.CommandLine$RunLast.handle(CommandLine.java:2423)
	at picocli.CommandLine$AbstractParseResultHandler.execute(CommandLine.java:2277)
	at picocli.CommandLine$RunLast.execute(CommandLine.java:2425)
	at picocli.CommandLine.execute(CommandLine.java:2174)
	at io.github.a2geek.clth.app.Main.main(Main.java:38)
	at java.base@21.0.7/java.lang.invoke.LambdaForm$DMH/sa346b79c.invokeStaticInit(LambdaForm$DMH)
```

## Gradle and Maven GAV

The libraries are published to Maven central and can be incorporated into your Java projects for unit testing.

<details>

<summary>Maven</summary>

```xml
<dependency>
    <groupId>io.github.a2geek</groupId>
    <artifactId>clth</artifactId>
    <version>2.0</version>
</dependency>
```

</details>
<details>

<summary>Gradle</summary>

```groovy
implementation("io.github.a2geek:clth:2.0")
```

</details>

## Using in a project

> Important note: If you use `System.exit()` *and* are doing a native compile, the agent that "catches" the exit call 
> interferes with the Graal native compile. This can be circumvented by separating the application from the command line
> testing. Please note the (app)[app/] and (app-tests)[app-tests/] structure in this project.

There are helper classes to assist in setting up the test cases and executing. However, as the developer, you will need to
stitch them together.

A sample unit test:

> Please note that the only argument required is the `TestSuite`. The other two arguments (`name`, `parameters`) are only
> used to give the test a human-readable name via the `@ParameterizedTest` annotation.

```java
@ParameterizedTest(name = "{1}: {2}")
@MethodSource("testCases")
public void test(TestSuite testSuite, String name, String parameters) {
    TestHarness.run(testSuite, JUnitHelper::execute, TestHarness.FilePreservation.DELETE);
}

public static Stream<Arguments> testCases() {
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
```

In addition, the Java agent needs to be added for unit tests -- *if you are using `System.exit()` in the application*.

> This is the Gradle configuration, but [junit5-system-exit](https://github.com/tginsberg/junit5-system-exit) also includes
> how to configure the tool for Maven as well.

```groovy
test {
    useJUnitPlatform()

    def junit5SystemExit = configurations.testRuntimeClasspath.files
            .find { it.name.contains('junit5-system-exit') }
    jvmArgumentProviders.add({["-javaagent:$junit5SystemExit"]} as CommandLineArgumentProvider)
}
```

## Config file

The configuration file is done through a yaml file. Note that file paths must work both in the project and out of the 
project.

At a high level, the config file has the following components: `commands`, `files`, and `tests`. Only `commands`
and `tests` are required.

### Commands

The commands block is used to tie a cli reference to an actual Java class and/or an executable.

```yaml
commands:
  <cli>:
    main-class: <fully qualified class with main method>
    system-exit: yes | no
    executable: <path to native compile result; allows glob patterns>
```

Use `main-class` and `system-exit` to use the Java test structure. Use `executable` to target the resulting executable.
Note that glob patterns are allowed.

Of special note, `system-exit` helps the Java tooling understand how the Java CLI components execute. When running in
a JVM, a command-line tool that calls `System.exit(...)` is (obviously) problematic. Currently, the test harness uses
[junit5-system-exit](https://github.com/tginsberg/junit5-system-exit), and it has some specific configuration instructions,
depending on how unit tests are being run (plain Java, Gradle, Maven). If you are using unit tests and the application in
question uses `System.exit()`, please visit this page to review your configuration.

### Files

The files section is intended to allow files to be dynamically generated as a temp file or to be used as validation.
Files are referenced as variables with a `$` prefix, and they can be referenced as a variable, in those cases where
any array based test uses different input names or content but is otherwise identical. Note that if the file is used
for `stdin`, then the content is used to populate the input stream.

```yaml
files:
  <filename>:
    type: text | binary | temporary
    content: <starting content>
    prefix: <prefix name for temporary file>
    suffix: <suffix name for temporary file>
```

The real variable is based on the type, which impacts the initial state of the file:
* `text` - The temp file simply has the textual content given.
* `binary` - The temp file has the binary content specified. The binary content is a series of bytes such as `20 fc 58` 
  would be a 6502 `JSR $FC58` instruction (for the Apple II).
* `temporary` - Creates a blank temp file and content is ignored.

### Tests

> Note that any file references will be _shared_ across the test suite. If there are unwanted changes to the test file,
> be certain that they are in independent suites.

These are a unit test, and it compromises a test "suite" of multiple steps.

```yaml
tests:
  - name: <test name>
    variables:
      # All arrays must be same length -- only iterated over, not matrixed
      arg1: [ "a", "b", "c" ]
      arg2: [ "d", "e", "f" ]
    steps:
      - command: <cli> command-with-flags $arg1 $arg2
      - command: <cli> command-no-flags
      - command: <cli> command-with-stdin
        stdin: file:src/test/resources/testfile.txt
      - command: <cli> command-with-stdin-alternate
        stdin: $filename
      - command: <cli> command-with-stdout
        stdout: |
          expected output here
```

The `variables` component is either a string or an array of strings. In the case of an array of strings, each array should be the same length. If
they do not match, it does not generate an error, but instead only executes the smallest set of combinations. That is if `arg1` were 2 items long,
and `arg2` were 5 items long, only the first two items from `arg2` will be used.

Note that `command` references the `cli` tool name. This should allow multiple tools to be utilized. Note that each tool needs to be defined
in the `commands:` section.

Finally, the steps array allows a sequence of commands. This is intended for a suite of tests where the tool produces or updates
data that is used subsequently. (For instance, it creates some content and then shows that content.) The options for each step are:
* `command` - a reference to the `cli` tool and all applicable arguments. Files and variables are referenced with a `$` prefix.
* `stdin` - sets the stdin for the process; a `file:` prefix searches for that file, a `$` reference uses a variable value or
  a file value, or is simply text to be used. The default is no input.
* `criteria` - the test criteria to apply to stderr and stdout.
* `stdout` - the expected text output. The default is no output.
* `stderr` - the expected error output. The default is no output.
 
The criteria structure is as follows: 
* `match` - the match criteria to apply. Default is `exact`.

  | Option       | Description                                                                                                    |
  |--------------|----------------------------------------------------------------------------------------------------------------|
  | `contains`   | True if the string is found within the output.                                                                 |
  | `exact`      | Strings must match exactly, including whitespace. (Default)                                                    |
  | `ignore`     | Ignore this match. Assume `true`.                                                                              |
  | `regex`      | Must match the regex. Note that regex is put into "dotall" mode, meaning `.` matches line terminators as well. |

* `whitespace` - indicates how to handle whitespace. Note that the whitespace condition is applied to expected and actual 
  values _before_ the match criteria. Default is `exact`.

  | Option   | Description                                                                                               |
  |----------|-----------------------------------------------------------------------------------------------------------|
  | `exact`  | Match all whitespace exactly. (Default)                                                                   |
  | `trim`   | Whitespace at beginning and ending of _each line_ is trimmed, and resulting strings must match exactly.   |
  | `ignore` | Ignore all whitespace for comparison. Also performs an implied trim to remove extra whitespace from ends. |
