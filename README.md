### Requirements

- Java 21.0.6
- Gradle 8.7

### Setup

Clone this repository:

`git clone --recurse-submodules https://github.com/man-netcat/funcons-truffle.git`

### Running the code generator

For the code generator, run the following:

`gradle :trufflegen:run`

### Test Cases

For the test cases, you can run the following:

`gradle :fctinterpreter:runTests`

### Interpreting a config file

Just pass a path to the interpreter like this

`gradle :fctinterpreter:run --args path/to/file.config`