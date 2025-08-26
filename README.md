### Setup

Clone this repository:

```bash
git clone --recurse-submodules https://github.com/man-netcat/funcons-truffle.git
cd funcons-truffle
```

Then just run:

```bash
./setup.sh
```

From here, you can run the tests as follows:

```bash
./gradlew cleanTest test
```

To run the interpreter on a single file, use the following:

```bash
./gradlew run --args "../CBS-beta/Funcons-beta/Computations/Normal/Flowing/tests/if-true-else.config"
```
