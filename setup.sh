#!/bin/bash

# First run fails...
./gradlew :fctlang:build

# This fixes it?? But running this first without first running :fctlang:build fails... too tired to fix this...
./gradlew clean :antlr:generateAllGrammars

# Second run is necessary... because... magic?
./gradlew :fctlang:build
