import argparse
import glob
import os

from icecream import ic  # NOTE: Debug library, remove me!
from pyparsing import (
    Group,
    Keyword,
    OneOrMore,
    Optional,
    ParseException,
    Suppress,
    Word,
    ZeroOrMore,
    alphanums,
    alphas,
    delimitedList,
    oneOf,
)

FUNCON = Keyword("Funcon")
TYPE = Keyword("Type")
DATATYPE = Keyword("Datatype")
ENTITY = Keyword("Entity")
METAVARIABLES = Keyword("Meta-variables")
ALIAS = Keyword("Alias")
RULE = Keyword("Rule")
KEYWORD = FUNCON | TYPE | DATATYPE | ENTITY | METAVARIABLES

IDENTIFIER = Word(alphas, alphanums + "-")
TYPEORVAR = Word(alphas, alphanums + "-'") | "_"
MODIFIER = oneOf("* + ?")

COMPUTES = "=>"
REWRITES_TO = "~>"


# Temp name
inner_type = (
    Optional(COMPUTES)
    + (TYPEORVAR | (Optional(Suppress("(")) + TYPEORVAR + Optional(Suppress(")"))))
    + Optional(MODIFIER)
)
type = Group(
    (inner_type | (Optional(Suppress("(")) + inner_type + Optional(Suppress(")"))))
    + Optional(MODIFIER),
)

value = Group(
    TYPEORVAR + Optional(MODIFIER),
)


def funcon_rule_parser():
    args = delimitedList(
        Group(
            TYPEORVAR + Optional(MODIFIER),
        ).setResultsName("args*")
    )
    entity = Group(
        Suppress("--")
        + TYPEORVAR
        + Optional(MODIFIER)
        + Suppress("(")
        + value.setResultsName("value")
        + Optional(Suppress(":") + type.setResultsName("type"))
        + Suppress(")")
    ).setResultsName("entity")
    rewrite = Group(
        Group(TYPEORVAR).setResultsName("identifier")
        + Suppress("(")
        + args
        + Suppress(")")
        + Optional(entity)
        + REWRITES_TO
        + Group(
            TYPEORVAR + Optional(MODIFIER),
        ).setResultsName("rewrites_to"),
    ).setResultsName("rules*")

    rule = RULE + rewrite

    return rule


def funcon_def_parser():
    params = delimitedList(
        Group(
            value.setResultsName("param") + Suppress(":") + type.setResultsName("type"),
        ).setResultsName("params*")
    )
    alias = ALIAS + Group(
        IDENTIFIER.setResultsName("alias")
        + Suppress("=")
        + IDENTIFIER.setResultsName("original"),
    ).setResultsName("aliases*")

    rule = funcon_rule_parser()

    funcon = FUNCON + Group(
        IDENTIFIER.setResultsName("name")
        + Suppress("(")
        + params
        + Suppress(")")
        + Suppress(":")
        + type.setResultsName("returns")
    ).setResultsName("funcon")
    funcon_definition = Group(
        funcon + ZeroOrMore(alias | rule),
    ).setResultsName("funcon_definitions*")
    return funcon_definition


def entity_parser():
    # TODO
    entity = ENTITY + ... + KEYWORD

    return entity


def type_def_parser():
    # TODO
    type = TYPE + ... + KEYWORD

    return type


def metavariables_parser():
    return METAVARIABLES + OneOrMore(
        Group(
            delimitedList(type.setResultsName("types*"))
            + Suppress("<:")
            + Group(IDENTIFIER + Optional(MODIFIER)).setResultsName("varname"),
        ).setResultsName("metavariables*")
    )


def build_parser():
    file = OneOrMore("#") + TYPEORVAR.setResultsName("filename")
    section = Group(
        Suppress("####") + TYPEORVAR,
    )
    index_line = Group(KEYWORD + TYPEORVAR + Optional(ALIAS + TYPEORVAR))
    index = Group(
        Suppress("[") + OneOrMore(index_line) + Suppress("]"),
    ).setResultsName("index")
    multiline_comment = Suppress("/*") + ... + Suppress("*/")
    metavariables = metavariables_parser()
    funcon_definitions = funcon_def_parser()
    entity = entity_parser()
    type_definition = type_def_parser()

    parser = OneOrMore(
        file
        | Suppress(index)
        | Suppress(section)
        | metavariables
        | funcon_definitions
        | entity
        | type_definition
    )
    return parser.ignore(multiline_comment)


def parse_file(file):
    try:
        parser = build_parser()
        result = parser.parseFile(file)
        ic(result.asDict())
    except ParseException as e:
        ic(ParseException.explain(e))


def main():
    parser = argparse.ArgumentParser(description="Parse .cbs files.")
    parser.add_argument(
        "-d",
        "--directory",
        help="Parse all .cbs files in the specified directory",
    )
    parser.add_argument(
        "-f",
        "--file",
        help="Parse the specified .cbs file",
    )
    args = parser.parse_args()

    if args.directory and args.file:
        raise ValueError("Specify either -d/--directory or -f/--file, not both.")

    if args.directory:
        pattern = os.path.join(args.directory, "**/*.cbs")
        cbs_files = glob.glob(pattern, recursive=True)
        for path in cbs_files:
            print(path)
            with open(path) as file:
                parse_file(file)
            print()
    elif args.file:
        print(args.file)
        with open(args.file) as file:
            parse_file(file)
    else:
        raise ValueError("Specify either -d/--directory or -f/--file.")


if __name__ == "__main__":
    main()
