import argparse
import glob
import os

from icecream import ic  # NOTE: Debug library, remove me!
from pyparsing import (
    Forward,
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
    nestedExpr,
    oneOf,
)

FUNCON = Keyword("Funcon")
TYPE = Keyword("Type")
DATATYPE = Keyword("Datatype")
ENTITY = Keyword("Entity")
METAVARIABLES = Keyword("Meta-variables")
ALIAS = Keyword("Alias")
RULE = Keyword("Rule")
BUILTINFUNCON = Keyword("Built-in Funcon")
KEYWORD = FUNCON | TYPE | DATATYPE | ENTITY | METAVARIABLES

IDENTIFIER = Word(alphas, alphanums + "-")
TYPEORVAR = Word(alphas, alphanums + "-'") | "_"

SUFFIX = oneOf("* + ?")
ENTITYMODIFIER = oneOf("! ?")
PREFIX = oneOf("~")

COMPUTES = "=>"
REWRITES_TO = oneOf("~> ->")

ENTITYSIGNIFIER = "--"


# Temp name
inner_type = (
    Optional(COMPUTES)
    + (TYPEORVAR | (nestedExpr(content=TYPEORVAR)))
    + Optional(SUFFIX)
)
type = Group(
    (inner_type | (nestedExpr(content=inner_type))) + Optional(SUFFIX),
)

value = Group(
    TYPEORVAR + Optional(SUFFIX),
)

param = value.setResultsName("value") + Optional(
    Suppress(":") + type.setResultsName("type")
)

params = Suppress("(") + delimitedList(Group(param)) + Suppress(")")

entitysig = Group(
    Suppress(ENTITYSIGNIFIER)
    + IDENTIFIER.setResultsName("name")
    + Optional(ENTITYMODIFIER)
    + params.setResultsName("params*"),
).setResultsName("entity")


def funcon_rule_parser():

    rewrite = Group(
        IDENTIFIER.setResultsName("identifier")
        + params.setResultsName("args*")
        + Optional(entitysig)
        + REWRITES_TO
        + value.setResultsName("rewrites_to"),
    ).setResultsName("rules*")

    rule = RULE + rewrite

    return rule


def funcon_def_parser():
    fsig = (
        params.setResultsName("params*")
        + Suppress(":")
        + type.setResultsName("returns")
    )

    funcon = FUNCON + Group(
        IDENTIFIER.setResultsName("name") + fsig,
    ).setResultsName("funcon")

    alias = ALIAS + Group(
        IDENTIFIER.setResultsName("alias")
        + Suppress("=")
        + IDENTIFIER.setResultsName("original"),
    ).setResultsName("aliases*")

    rule = funcon_rule_parser()

    funcon_definition = Group(
        funcon + ZeroOrMore(alias | rule),
    ).setResultsName("funcon_definitions*")
    return funcon_definition


def entity_parser():
    # TODO
    contextual = Forward()
    mutable = Forward()
    input = Forward()
    output = Forward()
    control = Forward()

    entity = contextual | mutable | input | output | control

    return entity


def type_def_parser():
    # TODO
    type = Forward()

    return type


def metavariables_parser():
    return METAVARIABLES + OneOrMore(
        Group(
            delimitedList(type).setResultsName("types*")
            + Suppress("<:")
            + Group(IDENTIFIER + Optional(SUFFIX)).setResultsName("varname"),
        ).setResultsName("metavariables*")
    )


def build_parser():
    file = OneOrMore("#") + TYPEORVAR.setResultsName("filename")

    index_line = Group(KEYWORD + TYPEORVAR + Optional(ALIAS + TYPEORVAR))

    index = Group(
        Suppress("[") + OneOrMore(index_line) + Suppress("]"),
    ).setResultsName("index")

    multiline_comment = Suppress("/*") + ... + Suppress("*/")

    section = OneOrMore("#") + TYPEORVAR

    metavariables = metavariables_parser()

    funcon_definitions = funcon_def_parser()

    entity = entity_parser()

    type_definition = type_def_parser()

    parser = OneOrMore(
        file | metavariables | funcon_definitions | entity | type_definition
    )
    return parser.ignore(multiline_comment | section | index)


def parse_file(file):
    try:
        parser = build_parser()
        result = parser.parseFile(file)
        ic(result.asDict())
    except ParseException as e:
        print(ParseException.explain(e))


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
