import argparse
import glob
import json
import os
from pathlib import Path

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

IDENTIFIER = Word(alphas + "_", alphanums + "_'-")

SUFFIX = oneOf("* + ?")
POLARITY = oneOf("! ?")
PREFIX = oneOf("~")

COMPUTES = "=>"
REWRITES_TO = oneOf("~> ->")
TRANSITION = Suppress("--->")

ENTITYSIGNIFIER = "--"

COLON = Suppress(":")
LPAR = Suppress("(")
RPAR = Suppress(")")
LANG = Suppress("<")
RANG = Suppress(">")
COMMA = Suppress(",")

expr = Forward()
params = Forward()
nested_expr = LPAR + Group(expr) + RPAR
atom = (IDENTIFIER + params) | nested_expr | IDENTIFIER
expr <<= Optional(COMPUTES) + Optional(PREFIX) + atom + Optional(SUFFIX)

value = expr("value")
type = COLON + expr("type")
param = Group(value + Optional(type))

params <<= LPAR + Optional(delimitedList(param)) + RPAR

entitysig = Group(
    Optional(ENTITYSIGNIFIER)
    + IDENTIFIER("name")
    + Optional(POLARITY)
    + params("params"),
)("entity")


def funcon_rule_parser():
    rewrite = Group(
        IDENTIFIER("identifier")
        + Optional(params("args"))
        + Optional(entitysig("with_entity"))
        + REWRITES_TO
        + expr("rewrites_to"),
    )("rules*")

    step = Group(
        IDENTIFIER("identifier")
        + Optional(params("args"))
        + TRANSITION
        + IDENTIFIER("identifier")
        + Optional(params("args"))
    )

    transition = Group(step + OneOrMore("-") + step)

    rule = Suppress(RULE) + (rewrite | transition)

    return rule


def funcon_alias_parser():
    return Suppress(ALIAS) + Group(
        IDENTIFIER("alias") + Suppress("=") + IDENTIFIER("original"),
    )


def funcon_def_parser():
    funcon = Suppress(FUNCON) + Group(
        IDENTIFIER("name")
        + Optional(params("params"))
        + Suppress(":")
        + expr("returns")
        + Optional(
            REWRITES_TO
            + Group(
                IDENTIFIER("name") + Optional(params("params")),
            )("rewrites_to")
        ),
    )

    alias = funcon_alias_parser()

    rule = funcon_rule_parser()

    funcon_definition = Group(
        funcon("definition") + ZeroOrMore(alias("aliases*") | rule),
    )("funcons*")

    return funcon_definition


def entity_parser():
    # TODO
    contextual = Forward()
    entitypart = Group(LANG + IDENTIFIER + COMMA + IDENTIFIER + params + RANG)
    mutable = entitypart + TRANSITION + entitypart

    # input, output and control
    ioc = IDENTIFIER + entitysig + REWRITES_TO + IDENTIFIER

    entity = Suppress(ENTITY) + Group(contextual | mutable | ioc)("entities*")

    return entity


def type_def_parser():
    # TODO
    type = Forward()

    return type


def datatype_parser():
    return Suppress(DATATYPE) + Group(
        expr("name") + Optional("::=") + expr("definition")
    )("datatypes*")


def metavariables_parser():
    return Suppress(METAVARIABLES) + OneOrMore(
        Group(
            delimitedList(expr)("types*")
            + Suppress("<:")
            + Group(IDENTIFIER + Optional(SUFFIX))("varname"),
        )("metavariables*")
    )


def build_parser():
    index_line = Group(KEYWORD + IDENTIFIER + Optional(ALIAS + IDENTIFIER))

    index = Group(
        Suppress("[") + OneOrMore(index_line) + Suppress("]"),
    )("index")

    multiline_comment = Suppress("/*") + ... + Suppress("*/")

    header = Suppress(OneOrMore("#") + IDENTIFIER)

    metavariables = metavariables_parser()

    funcons = funcon_def_parser()

    entity = entity_parser()

    type_definition = type_def_parser()

    datatype = datatype_parser()

    parser = OneOrMore(
        metavariables | funcons | entity | type_definition | datatype | header
    )

    return parser.ignore(multiline_comment | index)


def parse_file(filename, dump_json=False):
    with open(filename) as file:
        try:
            parser = build_parser()
            result = parser.parseFile(file)
            resasdict = result.asDict()
            if dump_json:
                with open(f"out/{Path(filename).stem}.json", "w") as f:
                    json.dump(resasdict, f, indent=2)
            ic(resasdict)

        except ParseException as e:
            print(ParseException.explain(e))


def main():
    if not os.path.isdir("out"):
        os.mkdir("out")

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
    parser.add_argument(
        "-j",
        "--json",
        help="Dump parsed data to JSON",
        action="store_true",
    )
    args = parser.parse_args()

    if args.directory and args.file:
        raise ValueError("Specify either -d/--directory or -f/--file, not both.")

    if args.directory:
        pattern = os.path.join(args.directory, "**/*.cbs")
        cbs_files = glob.glob(pattern, recursive=True)
        for path in cbs_files:
            print(path)
            parse_file(path, args.json)
            print()
    elif args.file:
        print(args.file)
        parse_file(args.file, args.json)
    else:
        raise ValueError("Specify either -d/--directory or -f/--file.")


if __name__ == "__main__":
    main()
