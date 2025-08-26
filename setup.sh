#!/bin/bash

# First run fails...
gradle :fctlang:build

# This fixes it?? But running this first without first running :fctlang:build fails... too tired to fix this...
gradle clean :antlr:generateAllGrammars

# Second run is necessary... because... magic?
gradle :fctlang:build
