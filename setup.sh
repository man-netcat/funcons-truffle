#!/bin/bash

gradle :fctlang:build
gradle clean :antlr:generateAllGrammars
gradle :fctlang:build
