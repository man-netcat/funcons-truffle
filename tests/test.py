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


def generate_test_function(parser, component):
    def test_function(self):
        parse_str(parser, component)

    return test_function


keyword_counters = defaultdict(lambda: 1)

for file in cbs_files:
    file_components = parse_file_components(file)
    for keyword, component in file_components.items():
        parser = parsers[keyword]

        for comp in component:
            test_func = generate_test_function(parser, comp)
            setattr(
                TestParserFunction,
                f"test_{keyword.lower()}_{keyword_counters[keyword]}",
                test_func,
            )

        keyword_counters[keyword] += 1
if __name__ == "__main__":
    main()
