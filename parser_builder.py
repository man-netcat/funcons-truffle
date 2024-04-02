import json
import re
from collections import defaultdict
from pathlib import Path
from pprint import pprint
from string import ascii_lowercase

from pyparsing import (
    Combine,
    FollowedBy,
    Forward,
    Group,
    Keyword,
    Literal,
    OneOrMore,
    Optional,
    Or,
    ParseException,
    ParserElement,
    StringEnd,
    Suppress,
    Word,
    ZeroOrMore,
    alphanums,
    delimitedList,
    infixNotation,
    nums,
    oneOf,
    opAssoc,
    restOfLine,
)

ParserElement.enablePackrat(None)


def encapsulate(expr, left, right):
    return Suppress(left) + Optional(expr) + Suppress(right)


BAR = Suppress(Literal("---") + OneOrMore("-"))
ASSERT = Keyword("Assert")
TYPE = Keyword("Type") | Keyword("Built-in Type")
DATATYPE = Keyword("Built-in Datatype") | Keyword("Datatype")
ENTITY = Keyword("Entity")
METAVARIABLES = Keyword("Meta-variables")
ALIAS = Keyword("Alias")
RULE = Keyword("Rule")
FUNCON = Keyword("Built-in Funcon") | Keyword("Auxiliary Funcon") | Keyword("Funcon")


BASEKEYWORD = ASSERT | FUNCON | TYPE | DATATYPE | ENTITY | METAVARIABLES

EXTKEYWORD = ALIAS | RULE
KEYWORD = BASEKEYWORD | EXTKEYWORD

IDENTIFIER = ~(KEYWORD | BAR) + Combine(
    Word(initChars=alphanums + '_"', bodyChars=alphanums + '-"') + ZeroOrMore("'")
)
FUNIDENTIFIER = ~(KEYWORD | BAR) + Word(ascii_lowercase + "-", asKeyword=True)
NUMBER = Combine(Optional("-") + Word(nums))

POLARITY = oneOf("! ?")
SUFFIX = oneOf("* + ? ^N")
PREFIX = oneOf("~ =>")
INFIX = oneOf("=> | &")

REWRITES_TO = Suppress("~>")
CONTEXTUALENTITY = Suppress("|-")
MAPSTO = Suppress("|->")
METAVARASSIGN = Suppress("<:")
DATATYPEASSIGN = Suppress("::=")
EQUALS = Suppress("==")
NOTEQUALS = Suppress("=/=")
EQUALITY = EQUALS | NOTEQUALS
COLON = Suppress(":")
COMMA = Suppress(",")
SEMICOLON = Suppress(";")

# Define forward expression for recursion
expr = Forward()

param = Group(
    expr("value") + Optional(COLON + expr("type")),
)

params = Group(encapsulate(delimitedList(param("params*")), "(", ")"))

fun_call = Forward()
fun_call <<= Group(
    FUNIDENTIFIER("name") + (params | fun_call | IDENTIFIER),
)("fun_call")

mapexpr = expr("value") + MAPSTO + expr("mapsto")

mapping = encapsulate(mapexpr | delimitedList(param("params*")), "{", "}")

listexpr = encapsulate(delimitedList(param("indices*")), "[", "]")

listindex = Group(IDENTIFIER("identifier") + listexpr)

nested = encapsulate(expr, "(", ")")

operands = Or(
    [
        listindex,
        listexpr,
        mapping,
        fun_call,
        nested,
        params,
        NUMBER,
        IDENTIFIER,
    ]
)

expr <<= infixNotation(
    operands,
    [
        (SUFFIX, 1, opAssoc.LEFT),
        (PREFIX, 1, opAssoc.RIGHT),
        (INFIX, 2, opAssoc.LEFT),
    ],
)

# Rules

# Input, Output and Control
ioc = Group(IDENTIFIER("name") + Optional(POLARITY("polarity")) + params)

computation = encapsulate(delimitedList(ioc), "--", "->")("ioc*") + Optional(
    NUMBER("sequence_number")
)

mutablentitysig = Group(
    encapsulate(expr("source") + COMMA + expr("target"), "<", ">"),
)("mutableentity")
mutableentity = Group(
    mutablentitysig("before") + computation + mutablentitysig("after")
)("mutableentityrewrite")


# Input/Output/Control entities (with optional context)
context = expr("context") + CONTEXTUALENTITY
ioc_entity = Group(
    Optional(context)
    + expr("before")
    + delimitedList(computation("computations*"), ";")
    + expr("after"),
)("rewrite")

# Type premise
typeexpr = Group(
    expr("value") + COLON + expr("type"),
)("typeexpr")

# Boolean premise
boolexpr = Group(
    expr("value") + ((EQUALS + expr("equals")) | (NOTEQUALS + expr("notequals")))
)("boolexpr")

#
rewriteexpr = Group(
    expr("value") + REWRITES_TO + expr("rewrites_to"),
)("rewriteexpr")

premise = ioc_entity | mutableentity | rewriteexpr | boolexpr | typeexpr
premises = OneOrMore(premise)

transition = Group(premises("premises*") + BAR + premise("rewritten"))

rule_def = transition | premise
rule = Group(Suppress(RULE) + rule_def("rules*"))

# Aliases

alias = Group(
    Suppress(ALIAS)
    + Group(IDENTIFIER("alias") + Suppress("=") + IDENTIFIER("original"))("aliases*")
)
aliases = ZeroOrMore(alias)

# Funcons

funcon_def = Group(
    IDENTIFIER("name")
    + Optional(params)
    + COLON
    + expr("returns")
    + Optional(
        REWRITES_TO + expr("rewrites_to"),
    )
)("definition")
funcon_parser = Group(
    Suppress(FUNCON) + funcon_def + ZeroOrMore(alias) + ZeroOrMore(rule)
)("funcons*")

# Entities

entitydef = mutableentity | ioc_entity
entity_parser = Group(Suppress(ENTITY) + entitydef + aliases)("entities*")

# Types

type_def = (
    expr("type")
    + Optional(METAVARASSIGN + expr("assigns"))
    + Optional(REWRITES_TO + expr("rewrites_to"))
)
type_parser = Group(Suppress(TYPE) + type_def + aliases)("types*")

# Datatypes

datatypedef = (
    expr("name") + Suppress(DATATYPEASSIGN | METAVARASSIGN) + expr("definition")
)
datatype_parser = Group(Suppress(DATATYPE) + datatypedef + aliases)("datatypes*")


# Metavars

metavar_type = IDENTIFIER + Optional(SUFFIX)
metavar_types = delimitedList(metavar_type)
metavar_def = Group(
    metavar_types("types*")
    + METAVARASSIGN
    + metavar_types("definition")
    + Optional(params)
)

metavar_defs = OneOrMore(metavar_def("metavars*"))
metavar_parser = Suppress(METAVARIABLES) + metavar_defs

# Assertions

assert_def = expr("expr") + Optional(mapping) + Suppress(EQUALS) + expr("equals")
assert_parser = Group(Suppress(ASSERT) + assert_def)("assertions*")


parsers = {
    "Entity": entity_parser,
    "Meta-variables": metavar_parser,
    "Type": type_parser,
    "Datatype": datatype_parser,
    "Funcon": funcon_parser,
    "Assert": assert_parser,
}

indexline = BASEKEYWORD + IDENTIFIER + Optional(ALIAS + IDENTIFIER)

indexlines = OneOrMore(indexline)

index = Suppress("[" + indexlines + "]")

multiline_comment = Suppress(Literal("/*") + ... + Literal("*/"))

header = Suppress(OneOrMore("#") + restOfLine)

remove = multiline_comment | index | header

file_parser = ZeroOrMore(
    entity_parser
    | metavar_parser
    | type_parser
    | datatype_parser
    | funcon_parser
    | assert_parser
).ignore(remove)

componentparser = ZeroOrMore(
    Combine(BASEKEYWORD + ... + Suppress(FollowedBy(BASEKEYWORD | StringEnd())))
)


def clean_text(string):

    removed = remove.transformString(string).strip()

    newlines_stripped = re.sub(r"\n+", "\n", removed)

    return newlines_stripped


def exception_handler(func):
    def wrapper(*args, **kwargs):
        try:
            result = func(*args, **kwargs)
            return result
        except ParseException as e:
            e: ParseException
            sep = "~" * 70
            errstr = f"\n{sep}\n{e.args[0]}\n{sep}\n{ParseException.explain(e)}"
            raise ParseException(errstr) from None
        except RecursionError as e:
            raise ParseException("Recursionerror :(") from None

    return wrapper


@exception_handler
def parse_str(parser, str, print_res=False):
    res = parser.parseString(str, parseAll=True)
    if print_res:
        pprint(res.asDict())
    return res


@exception_handler
def parse_file_components(path) -> dict:
    components = defaultdict(lambda: [])
    with open(path, "r") as file:
        text = file.read()
        cleaned = clean_text(text)
        try:
            strings = componentparser.parseString(cleaned, parseAll=True)
        except:
            print("componentparser broken?")
            exit()
    for component in strings:
        splitline = component.split()
        keyword = splitline[0]
        if keyword in ["Auxiliary", "Built-in"]:
            keyword = splitline[1]
        components[keyword].append(component)
    return components


@exception_handler
def parse_file(path, dump_json=False, print_res=False):
    with open(path, "r") as f:
        text = f.read()
    cleaned = clean_text(text)
    res = file_parser.parseString(cleaned, parseAll=True)
    data = res.asDict()
    if print_res:
        pprint(data)
    if dump_json:
        filename = Path(path).stem
        json_path = f"out/{filename}.json"
        with open(json_path, "w") as f:
            json.dump(data, f)
            print(f"Exported to {json_path}")
