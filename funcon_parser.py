import glob
import os

from pyparsing import (
    Each,
    Group,
    Keyword,
    LineEnd,
    OneOrMore,
    Optional,
    ParseException,
    Suppress,
    Word,
    alphanums,
    alphas,
    delimitedList,
    oneOf,
)


def funcon_definition_parser():
    MODIFIER = oneOf("* + ?")

    funcon_name = Word(alphas, alphanums + "-")
    type_name = Word(alphas, alphanums + "-_") | "_"
    var_name = Word(alphas, alphanums + "-_") | "_"

    var = var_name + Optional(MODIFIER)

    type = (
        Optional("=>")
        + (type_name | Optional(Suppress("(")) + type_name + Optional(Suppress(")")))
        + Optional(MODIFIER)
    )
    type_seq = (
        type | (Optional(Suppress("(")) + type + Optional(Suppress(")")))
    ) + Optional(MODIFIER)

    arg = Group(var + Suppress(":") + type_seq)
    arg_list = Suppress("(") + delimitedList(arg, ",") + Suppress(")")

    funcon_signature = arg_list + Suppress(":") + type_seq

    funcon_declaration = funcon_name + funcon_signature
    funcon = Keyword("Funcon") + LineEnd() + funcon_declaration
    return funcon


def build_parser():
    filename = Suppress("###") + Word(alphas) + LineEnd()
    parser = filename + Each(
        [
            OneOrMore(funcon_definition_parser()),
        ]
    )
    empty_line = Suppress(LineEnd())
    return parser.ignore(empty_line)


def parse_file(file):
    try:
        parser = build_parser()
        print("Parsed result:", parser.parseString(file.read()).asList())
    except ParseException as e:
        print(ParseException.explain(e))
    # print()


def main():
    funcondir = "/home/rick/workspace/thesis/CBS-beta/Funcons-beta/Computations/Normal"
    pattern = os.path.join(funcondir, "**/*.cbs")
    cbs_files = glob.glob(pattern, recursive=True)
    for path in cbs_files:
        print(path)
        with open(path, "r") as file:
            parse_file(file)


if __name__ == "__main__":
    main()
