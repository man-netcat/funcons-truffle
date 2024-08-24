import configparser
import glob
import os
from collections import defaultdict
from pathlib import Path
from unittest import TestCase, main

from parser_builder import (
    cbs_file_parser,
    config_file_parser,
    get_parser,
    parse_file,
    parse_file_components,
    parse_str,
)


class TestCBSParser(TestCase):
    pass


class TestFCTParser(TestCase):
    pass


config = configparser.ConfigParser()
config.read("./tests/test.cfg")
funcon_dir = config.get("Paths", "funcon-dir")


def generate_cbs_parse_function(parser, case):
    def test_function(self):
        res = parse_str(parser, case)
        res.asDict()

    return test_function


def generate_fct_parse_function(file):
    def test_function(self):
        res = parse_file(config_file_parser, file)
        res.asDict()

    return test_function


def build_cbs_test_functions():
    cbs_pattern = os.path.join(funcon_dir, "**/*.cbs")
    cbs_files = glob.glob(cbs_pattern, recursive=True)
    allcases = defaultdict(lambda: [])
    for file in cbs_files:
        cases = parse_file_components(file)
        for keyword, case in cases.items():
            allcases[keyword].extend(case)

    # Dynamically generate test functions for each test case
    for keyword, cases in allcases.items():
        parser = get_parser(keyword)

        for i, case in enumerate(cases, 1):
            test_func = generate_cbs_parse_function(parser, case)
            setattr(TestCBSParser, f"test_{keyword.lower()}_{i}", test_func)


def build_fct_test_functions():
    fct_pattern = os.path.join(funcon_dir, "**/*.config")
    fct_files = glob.glob(fct_pattern, recursive=True)
    for file in fct_files:
        test_func = generate_fct_parse_function(file)
        test_name = Path(file).stem
        setattr(TestFCTParser, f"test_{test_name}", test_func)


build_cbs_test_functions()
# build_fct_test_functions()

if __name__ == "__main__":
    main()
