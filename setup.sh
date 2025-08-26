#!/bin/bash

gradle :fctlang:build
gradle :antlr:generateAllGrammars
gradle :fctlang:build
