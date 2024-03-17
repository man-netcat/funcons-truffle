import configparser
import glob
import os
from collections import defaultdict
from unittest import TestCase, main

from funcon_parser import (
    alias_parser,
    assert_parser,
    datatype_parser,
    entity_parser,
    funcon_def_parser,
    funcon_rule_parser,
    metavariables_parser,
    type_def_parser,
)
from pyparsing import (
    Combine,
    Keyword,
    LineEnd,
    OneOrMore,
    Optional,
    Or,
    ParseException,
    Suppress,
    White,
    Word,
    ZeroOrMore,
    alphas,
    alphanums,
    oneOf,
    restOfLine,
)

funcs = {
    "Entity": entity_parser,
    "Meta-variables": metavariables_parser,
    "Type": type_def_parser,
    "Alias": alias_parser,
    "Datatype": datatype_parser,
    "Funcon": funcon_def_parser,
    "Rule": funcon_rule_parser,
    "Assert": assert_parser,
}

ALIAS = Keyword("Alias")
IDENTIFIER = Combine(Word(alphas + "_", alphanums + "_-") + ZeroOrMore("'"))
KEYWORD = Or([Keyword(keyword) for keyword in funcs.keys()])
OPTKEYWORD = Or([Keyword(keyword) for keyword in ["Auxiliary", "Built-in"]])

indexlines = KEYWORD + IDENTIFIER + Optional(ALIAS + IDENTIFIER)
index = Suppress("[") + OneOrMore(indexlines) + Suppress("]")
header = Suppress(OneOrMore("#") + ... + LineEnd())
multiline_comment = Suppress("/*") + ... + Suppress("*/")
IGNORE = multiline_comment | index | header

indented_line = Suppress(White(min=2)) + restOfLine.ignore(IGNORE)
component_start = (
    Optional(OPTKEYWORD) + KEYWORD + ~oneOf("# /* */ [ ]") + restOfLine.ignore(IGNORE)
)
component = Combine(component_start + OneOrMore(indented_line), " ")

parser = ZeroOrMore(component)
parser = parser.ignore(IGNORE)


# Function to parse file and extract components
def parse_components(file_path):
    with open(file_path, "r") as file:
        try:
            return parser.parseFile(file)
        except ParseException as e:
            errstr = f"\n{'-'*70}\n{ParseException.explain(e)}"
            raise ParseException(errstr) from None


class TestParserFunction(TestCase):
    pass


allcases = defaultdict(lambda: [])
config = configparser.ConfigParser()
config.read("./tests/test.cfg")
funcon_dir = config.get("Paths", "funcon-dir")
pattern = os.path.join(funcon_dir, "**/*.cbs")
cbs_files = glob.glob(pattern, recursive=True)

# Parse files and extract components
for path in cbs_files:
    res = parse_components(path)
    for line in res:
        # print(line)
        splitline = line.split()
        keyword = splitline[0]
        if keyword in ["Auxiliary", "Built-in"]:
            keyword = splitline[1]
        allcases[keyword].append(line)

# Dynamically generate test functions for each test case
for keyword, cases in allcases.items():
    func = funcs[keyword]

    for i, case in enumerate(cases, 1):

        def generate_test_function(func, case):
            def test_function(_):
                try:
                    func().parseString(case)
                except ParseException as e:
                    errstr = f"\n{'-'*70}\n{ParseException.explain(e)}"
                    raise ParseException(errstr) from None

            return test_function

        test_func = generate_test_function(func, case)
        setattr(TestParserFunction, f"test_{func.__name__}_{i}", test_func)

if __name__ == "__main__":
    main()
