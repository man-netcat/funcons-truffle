import argparse
import glob
import json
import os
from pathlib import Path

from icecream import ic  # NOTE: Debug library, remove me!
from pyparsing import (
    Combine,
    Forward,
    Group,
    Keyword,
    Literal,
    OneOrMore,
    Optional,
    ParseException,
    ParserElement,
    Suppress,
    White,
    Word,
    ZeroOrMore,
    alphanums,
    delimitedList,
    infixNotation,
    oneOf,
    opAssoc,
    restOfLine,
)

ASSERT = Keyword("Assert")
FUNCON = Keyword("Funcon")
TYPE = Keyword("Type")
DATATYPE = Keyword("Datatype")
ENTITY = Keyword("Entity")
METAVARIABLES = Keyword("Meta-variables")
ALIAS = Keyword("Alias")
RULE = Keyword("Rule")
BUILTIN = Keyword("Built-in")
AUXILIARY = Keyword("Auxiliary")
OPTKEYWORD = Optional(AUXILIARY | BUILTIN)
KEYWORD = ASSERT | FUNCON | TYPE | DATATYPE | ENTITY | METAVARIABLES | ALIAS | RULE

IDENTIFIER = Combine(Word(alphanums + "_-") + ZeroOrMore("'"))

POLARITY = oneOf("! ?")
SUFFIX = oneOf("* + ?")
PREFIX = oneOf("~ =>")
INFIX = oneOf("=> == =/=")

REWRITES_TO = Suppress("~>")
CONTEXTUALENTITY = Suppress("|-")
MAPSTO = Suppress("|->")

EQUALS = Suppress("==")
NOTEQUALS = Suppress("=/=")
COLON = Suppress(":")
LPAR = Suppress("(")
RPAR = Suppress(")")
LANG = Suppress("<")
RANG = Suppress(">")
LCUR = Suppress("{")
RCUR = Suppress("}")
LBRA = Suppress("[")
RBRA = Suppress("]")
COMMA = Suppress(",")
BAR = Suppress(OneOrMore("-"))
EMPTY = Group(LPAR + White() + RPAR)
BOOL = Literal("true") | Literal("false")
STRING = Suppress('"') + IDENTIFIER + Literal('"')

# Define forward expression for recursion
expr = Forward()

param = Group(expr("value") + Optional(COLON + expr("type")))
params = LPAR + Optional(delimitedList(param("params*"))) + RPAR
fun_call = Group(IDENTIFIER("fun") + params)
mapexpr = LCUR + Optional((expr("value") + MAPSTO + expr("mapsto")) | param) + RCUR
listexpr = LBRA + Optional(delimitedList(param("indices*"))) + RBRA
listindex = Group(IDENTIFIER("identifier") + listexpr)

entitysig = Group(IDENTIFIER("name") + Optional(POLARITY("polarity")) + params)

STEP = (
    Suppress("--") + delimitedList(entitysig("entities*")) + Suppress("->")
) | Suppress("--->")

mutableentity = Group(LANG + delimitedList(expr) + RANG)

operands = (
    listindex
    | listexpr
    | mapexpr
    | mutableentity
    | fun_call
    | EMPTY
    | BOOL
    | STRING
    | IDENTIFIER
)

ParserElement.enablePackrat(None)
expr <<= infixNotation(
    operands,
    [
        (SUFFIX, 1, opAssoc.LEFT),
        (PREFIX, 1, opAssoc.RIGHT),
        (INFIX, 2, opAssoc.LEFT),
    ],
)


contextualentity = Group(
    Optional(expr("context") + CONTEXTUALENTITY)
    + (expr("before") + (STEP | REWRITES_TO) + expr("after"))
)


def funcon_rule_parser():
    premise = (expr("value") + COLON + expr("type")) | contextualentity
    transition = OneOrMore(premise("premises*")) + BAR + premise("rewritten")

    return Group(Suppress(RULE) + Group(transition | premise))("rules*")


def alias_parser():
    return Group(
        Suppress(ALIAS) + IDENTIFIER("alias") + Suppress("=") + IDENTIFIER("original"),
    )("aliases*")


def funcon_def_parser():
    return Group(
        Suppress(OPTKEYWORD + FUNCON)
        + IDENTIFIER("name")
        + Optional(
            params("params"),
        )
        + COLON
        + expr("returns")
        + Optional(
            REWRITES_TO + expr("rewrites_to"),
        )
    )("definition")


def funcon_parser():
    funcon = funcon_def_parser()

    alias = alias_parser()

    rule = funcon_rule_parser()

    return Group(
        funcon + ZeroOrMore(alias) + ZeroOrMore(rule),
    )("funcons*")


def entity_parser():
    return Group(
        Suppress(OPTKEYWORD + ENTITY) + (contextualentity | mutableentity),
    )("entities*")


def type_def_parser():
    alias = alias_parser()

    typedef = IDENTIFIER("name") + Optional(Suppress(REWRITES_TO) + expr("value"))

    return Group(
        Suppress(OPTKEYWORD + TYPE) + typedef + ZeroOrMore(alias("aliases*")),
    )("types*")


def datatype_parser():
    return Group(
        Suppress(OPTKEYWORD + DATATYPE)
        + expr("name")
        + Optional("::=")
        + expr("definition")
    )("datatypes*")


def metavariables_parser():
    return Suppress(METAVARIABLES) + OneOrMore(
        Group(
            delimitedList(expr("types*")) + Suppress("<:") + expr("definition"),
        )("metavariables*")
    )


def assert_parser():
    return Group(
        Suppress(ASSERT) + expr("expr") + Suppress(EQUALS) + expr("equals"),
    )("assertions*")


def build_parser():
    indexlines = KEYWORD + IDENTIFIER + Optional(ALIAS + IDENTIFIER)

    index = Suppress("[") + OneOrMore(indexlines) + Suppress("]")

    multiline_comment = Suppress("/*") + ... + Suppress("*/")

    header = Suppress(OneOrMore("#") + restOfLine)

    metavariables = metavariables_parser()

    funcons = funcon_parser()

    entity = entity_parser()

    typedef = type_def_parser()

    datatype = datatype_parser()

    assertion = assert_parser()

    parser = ZeroOrMore(
        metavariables | funcons | entity | typedef | datatype | assertion
    )

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
        except RecursionError as e:
            print("recursion error :(")


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
