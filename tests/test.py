import configparser
import glob
import os
from collections import defaultdict
from unittest import TestCase, main

from parser_builder import parse_file_components, parse_str, parsers


class TestParserFunction(TestCase):
    pass


config = configparser.ConfigParser()
config.read("./tests/test.cfg")
funcon_dir = config.get("Paths", "funcon-dir")
pattern = os.path.join(funcon_dir, "**/*.cbs")
cbs_files = glob.glob(pattern, recursive=True)


def generate_test_function(parser, case):
    def test_function(self):
        res = parse_str(parser, case)
        res.asDict()

    return test_function


allcases = defaultdict(lambda: [])
for file in cbs_files:
    cases = parse_file_components(file)
    for keyword, case in cases.items():
        allcases[keyword].extend(case)


# Dynamically generate test functions for each test case
for keyword, cases in allcases.items():
    parser = parsers[keyword]

    for i, case in enumerate(cases, 1):
        test_func = generate_test_function(parser, case)
        setattr(TestParserFunction, f"test_{keyword.lower()}_{i}", test_func)

if __name__ == "__main__":
    main()
