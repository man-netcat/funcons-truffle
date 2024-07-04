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
    MatchFirst,
    OneOrMore,
    Optional,
    ParseException,
    ParserElement,
    StringEnd,
    Suppress,
    White,
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


# Adds custom values to a pyparsing result object
def add_result_values(parse_result, values):
    def parseAction(toks, values: dict):
        for key, value in values.items():
            toks[key] = value
        return toks

    # return parse_result.setParseAction(lambda toks: parseAction(toks, values))
    return parse_result


def encapsulate(expr, left="(", right=")"):
    return Suppress(left) + Optional(expr) + Suppress(right)


BAR = Suppress(Combine(Literal("---") + OneOrMore("-"))).setName("---")
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
KEYWORD = (BASEKEYWORD | EXTKEYWORD).setName("keyword")

IDENTIFIER = Combine(
    ~(KEYWORD | BAR)
    + Word(initChars=alphanums + '_"', bodyChars=alphanums + '-"')
    + ZeroOrMore("'")
).setName("identifier")
FUNIDENTIFIER = Combine(
    ~(KEYWORD | BAR) + Word(ascii_lowercase + "-", asKeyword=True)
).setName("funidentifier")
NUMBER = (
    Combine(Optional("-") + Word(nums))
    .setName("number")
    .setParseAction(lambda toks: int(toks[0]))
)

POLARITY = oneOf("! ?").setName("polarity")
SUFFIX = oneOf("* + ? ^N").setName("suffix")
PREFIX = oneOf("~ =>").setName("prefix")
INFIX = oneOf("=> | &").setName("infix")

REWRITES_TO = Suppress("~>")
STEP = Suppress("--->")
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

returnempty = lambda toks: [[]]
EMPTY = Combine(
    Group(encapsulate(White())),
).setParseAction(returnempty)
EMPTYMAP = Combine(
    Group(encapsulate(White(), "{", "}")),
).setParseAction(returnempty)
EMPTYLIST = Combine(
    Group(encapsulate(White(), "[", "]")),
).setParseAction(returnempty)

# Define forward expression for recursion
expr = Forward()

param = Group(expr("value") + Optional(COLON + expr("type")))

params = encapsulate(delimitedList(param("params*")))

fun_call = Forward()
fun_call <<= Group(
    FUNIDENTIFIER("fun") + (EMPTY("params") | params | fun_call | IDENTIFIER),
)

mapexpr = expr("value") + MAPSTO + expr("mapsto")

mapping = EMPTYMAP | encapsulate(mapexpr | delimitedList(param), "{", "}")

listexpr = EMPTYLIST | encapsulate(delimitedList(param("indices*")), "[", "]")

listindex = Group(IDENTIFIER("identifier") + listexpr)

nested = encapsulate(expr)

operands = MatchFirst(
    [
        EMPTY,
        EMPTYMAP,
        EMPTYLIST,
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

# Actions (Input, Output and Control)
action = Group(IDENTIFIER("name") + Optional(POLARITY("polarity")) + params)

step = Group(
    encapsulate(delimitedList(action("actions*")), "--", "->")
    + Optional(NUMBER("sequence_number"))
)

mutablesig = encapsulate(expr("source") + COMMA + expr("target"), "<", ">")
mutableexpr = mutablesig("term") + step + mutablesig("rewrites_to")


# Computation steps
stepexpr = (
    Optional(expr("context") + CONTEXTUALENTITY)
    + expr("term")
    + (STEP | delimitedList(step("steps*"), ";"))
    + expr("rewrites_to")
)


# Type premise
typeexpr = expr("value") + COLON + expr("type")


# Boolean premise
boolexpr = expr("value") + ((EQUALS + expr("equals")) | (NOTEQUALS + expr("notequals")))


# Rewrite premise
rewriteexpr = expr("term") + REWRITES_TO + expr("rewrites_to")


premise = MatchFirst(
    [
        add_result_values(parser, {"premise_type": value})
        for value, parser in zip(
            [
                "computation_expr",
                "mutable_expr",
                "rewrite_expr",
                "bool_expr",
                "type_expr",
            ],
            [
                stepexpr,
                mutableexpr,
                rewriteexpr,
                boolexpr,
                typeexpr,
            ],
        )
    ]
)

transition = OneOrMore(Group(premise)("premises*")) + BAR + Group(premise)("conclusion")
rule_def = MatchFirst(
    [
        add_result_values(transition, {"rule_type": "transformation"}),
        add_result_values(premise, {"rule_type": "simple_rewrite"}),
    ]
)
rule_parser = Group(Suppress(RULE) + rule_def)
rules = ZeroOrMore(rule_parser("rules*"))

# Aliases

alias_parser = Group(
    Suppress(ALIAS) + IDENTIFIER("alias_id") + Suppress("=") + IDENTIFIER("orig_id")
)
aliases = ZeroOrMore(alias_parser("aliases*"))

# Funcons

funcon_parser = Group(
    Suppress(FUNCON)
    + IDENTIFIER("name")
    + Optional(params)
    + COLON
    + expr("returns")
    + Optional(
        REWRITES_TO + expr("rewrites_to"),
    )
)("definition")
funcon_parser = Group(funcon_parser + aliases + rules)("funcons*")

# Entities

entity_parser = Group(
    Suppress(ENTITY) + (mutableexpr | stepexpr) + aliases,
)("entities*")

# Types

type_def = (
    expr("type")
    + Optional(METAVARASSIGN + expr("assigns"))
    + Optional(REWRITES_TO + expr("rewrites_to"))
)
type_parser = Group(Suppress(TYPE) + type_def + aliases)("types*")

# Datatypes

datatypedef = (
    (IDENTIFIER("name") + Optional(params))("definition")
    + Suppress(DATATYPEASSIGN | METAVARASSIGN)
    + expr("definition")
)
datatype_parser = Group(Suppress(DATATYPE) + datatypedef + aliases)("datatypes*")


# Metavars

metavar_type = IDENTIFIER + Optional(SUFFIX)
metavar_def = Group(
    delimitedList(metavar_type("types*"))
    + METAVARASSIGN
    + metavar_type("definition")
    + Optional(params)
)

metavar_defs = OneOrMore(metavar_def("metavars*"))
metavar_parser = Suppress(METAVARIABLES) + metavar_defs

# Assertions

assert_def = expr("term") + Optional(mapping) + Suppress(EQUALS) + expr("equals")
assert_parser = Group(Suppress(ASSERT) + assert_def)("assertions*")

indexline = BASEKEYWORD + IDENTIFIER + Optional(ALIAS + IDENTIFIER)

indexlines = OneOrMore(indexline)

index = Suppress("[" + indexlines + "]")

multiline_comment = Suppress(Literal("/*") + ... + Literal("*/"))

header = Suppress(OneOrMore("#") + restOfLine)
slashcomment = Suppress(Literal("//") + restOfLine)

remove = multiline_comment | index | header | slashcomment

cbs_file_parser = ZeroOrMore(
    entity_parser
    | metavar_parser
    | type_parser
    | datatype_parser
    | funcon_parser
    | assert_parser
).ignore(remove)

componentparser = ZeroOrMore(
    Combine(KEYWORD + ... + Suppress(FollowedBy(KEYWORD | StringEnd())))
)


# .config file parser
def config_component(name):
    return Keyword(name) + COLON + expr(name) + SEMICOLON


config_file_parser = (
    Keyword("general")
    + encapsulate(config_component("funcon-term"), "{", "}")
    + Keyword("tests")
    + encapsulate(
        config_component("result-term") + config_component("standard-out"),
        "{",
        "}",
    )
)


def clean_text(string):

    removed = remove.transformString(string).strip()

    newlines_stripped = re.sub(r"\n+", "\n", removed)

    return newlines_stripped


def exception_handler(func):
    def wrapper(*args, **kwargs):
        sep = "~" * 70
        try:
            result = func(*args, **kwargs)
            return result
        except ParseException as e:
            e: ParseException
            errstr = f"\n{sep}\n{e.args[0]}\n{sep}\n{ParseException.explain(e)}"
            raise ParseException(errstr) from None
        except RecursionError as e:
            raise ParseException(
                f"\n{sep}\nRecursion error in component:\n{args[1]}"
            ) from None

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
        except Exception as e:
            print(e)
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
def parse_file(parser, path, dump_json=False, print_res=False):
    with open(path, "r") as f:
        text = f.read()
    cleaned = clean_text(text)
    res = parser.parseString(cleaned, parseAll=True)
    if print_res:
        data = res.asDict()
        pprint(data)
    if dump_json:
        data = res.asDict()
        filename = Path(path).stem
        json_path = f"out/{filename}.json"
        with open(json_path, "w") as f:
            json.dump(data, f)
            print(f"Exported to {json_path}")
    return res


def parse_cbs_file(path, dump_json=False, print_res=False):
    return parse_file(cbs_file_parser, path, dump_json, print_res)


def get_parser(keyword):
    parsers = {
        "Entity": entity_parser,
        "Meta-variables": metavar_parser,
        "Type": type_parser,
        "Datatype": datatype_parser,
        "Funcon": funcon_parser,
        "Assert": assert_parser,
        "Rule": rule_parser,
        "Alias": alias_parser,
    }

    return parsers[keyword]
