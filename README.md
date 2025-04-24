### Requirements

- Java 11

### Setup

Clone this repository:

`git clone --recurse-submodules https://github.com/man-netcat/funcons-truffle.git`

### Running the code generator

For the code generator, run the following:

`./gradlew :trufflegen:run`

### Test Cases

For the test cases, you can run the following:

`./gradlew :fctinterpreter:runTests`

### Interpreting a config file

Just pass a path to the interpreter like this

`./gradlew :fctinterpreter:run --args path/to/file.config`