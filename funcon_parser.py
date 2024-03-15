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
    LineEnd,
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
BUILTIN = Keyword("Built-in")

IDENTIFIER = Word(alphas + "_", alphanums + "_-") + ZeroOrMore("'")

SUFFIX = oneOf("* + ?")
POLARITY = oneOf("! ?")
PREFIX = oneOf("~")

COMPUTES = "=>"
REWRITES_TO = Suppress("~>")
CONTEXTUALENTITY = Suppress("|-")

COLON = Suppress(":")
LPAR = Suppress("(")
RPAR = Suppress(")")
LANG = Suppress("<")
RANG = Suppress(">")
COMMA = Suppress(",")

# Define forward expressions for recursion
expr = Forward()
params = Forward()

nested_expr = LPAR + Group(expr) + RPAR
fun_call = IDENTIFIER("name") + params("params")
expr <<= (
    Optional(COMPUTES)
    + Optional(PREFIX)
    + (fun_call | nested_expr | IDENTIFIER)
    + Optional(SUFFIX)
)

value = expr("value")
type = COLON + expr("type")
param = Group(value + Optional(type))
params <<= LPAR + Optional(delimitedList(param)) + RPAR

entitysig = (IDENTIFIER("name") + Optional(POLARITY)("polarity") + params("params"))(
    "entity"
)
withentity = delimitedList(entitysig)
STEP = Suppress("--->") | (Suppress("--") + withentity + Suppress("->"))

mutableentitypart = Group(LANG + expr("expr1") + COMMA + expr("expr2") + RANG)
mutableentity = Group(mutableentitypart("before") + STEP + mutableentitypart("after"))

contextualentity = Group(
    expr("contextualentity") + CONTEXTUALENTITY + expr("before") + STEP + expr("after")
)


def funcon_rule_parser():
    rewrite = Group(expr("before") + (REWRITES_TO | STEP) + expr("after"))

    transition = Group(
        rewrite("original") + Suppress(OneOrMore("-")) + rewrite("rewritten")
    )

    rule = Suppress(RULE) + (transition | rewrite)

    return rule


def alias_parser():
    return Suppress(ALIAS) + Group(
        IDENTIFIER("alias") + Suppress("=") + IDENTIFIER("original"),
    )


def funcon_def_parser():
    funcon = (
        Suppress(Optional(BUILTIN))
        + Suppress(FUNCON)
        + Group(
            expr
            + Suppress(":")
            + expr("returns")
            + Optional(REWRITES_TO + expr("rewrites_to"))
        )
    )

    alias = alias_parser()

    rule = funcon_rule_parser()

    funcons = Group(
        funcon("definition") + ZeroOrMore(alias("aliases*") | rule("rules*")),
    )("funcons*")

    return funcons


def entity_parser():
    ioc = expr + STEP + expr

    entity = Suppress(ENTITY) + Group(contextualentity | mutableentity | ioc)(
        "entities*"
    )

    return entity


def type_def_parser():
    alias = alias_parser()

    type = IDENTIFIER("name") + Optional(Suppress(REWRITES_TO) + expr("value"))

    types = Group(
        Optional(Suppress(BUILTIN))
        + Suppress(TYPE)
        + type("definition")
        + ZeroOrMore(alias("aliases*")),
    )("types*")

    return types


def datatype_parser():
    return Suppress(DATATYPE) + Group(
        expr("name") + Optional("::=") + expr("definition")
    )("datatypes*")


def metavariables_parser():
    return Suppress(METAVARIABLES) + OneOrMore(
        Group(
            delimitedList(expr("types*"))
            + Suppress("<:")
            + Group(IDENTIFIER + Optional(SUFFIX))("varname"),
        )("metavariables*")
    )


def build_parser():
    index = Suppress("[") + ... + Suppress("]")

    multiline_comment = Suppress("/*") + ... + Suppress("*/")

    header = Suppress(OneOrMore("#") + ... + LineEnd())

    metavariables = metavariables_parser()

    funcons = funcon_def_parser()

    entity = entity_parser()

    typedef = type_def_parser()

    datatype = datatype_parser()

    parser = OneOrMore(metavariables | funcons | entity | typedef | datatype)

    return parser.ignore(multiline_comment | index | header)


def parse_file(filename, dump_json=False) -> dict:
    with open(filename) as file:
        try:
            parser = build_parser()
            res: dict = parser.parseFile(file).asDict()
            if dump_json:
                with open(f"out/{Path(filename).stem}.json", "w") as f:
                    json.dump(res, f, indent=2)
            return res

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
            res = parse_file(path, args.json)
            ic(res)
            print()
    elif args.file:
        print(args.file)
        res = parse_file(args.file, args.json)
        ic(res)
    else:
        raise ValueError("Specify either -d/--directory or -f/--file.")


if __name__ == "__main__":
    main()
