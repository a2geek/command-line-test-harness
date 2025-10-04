# Command Line Test Harness

An experimental, declarative test harness for Java applications with command-line interfaces that also build a native
executable. This project aims to allow a test to be declared, run as a normal unit test to verify functionality within
an IDE as well as run the same set of tests from a command-line tool.

## Config file

The configuration file is done through a yaml file. Note that file paths must work both in the project and out of the 
project.

> Expect changes around how to specify files; with subprojects within a project, the test files likely will differ 
> when running as a unit test and when running as a independent command-line tool. (Simply because the working directory
> will be different.)

```yaml
commands:
  <cli>:
    main-class: <fully qualified class with main method>
    system-exit: yes | no
    executable: <path to native compile result; allows glob patterns>

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
      - command: <cli> command-with-stdout
        stdout: file:src/test/resources/expected-output.txt
```
