import json
import re
from collections import defaultdict
from pprint import pprint

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
    oneOf,
    opAssoc,
    restOfLine,
)


def encapsulate(expr, left, right):
    return (
        Suppress(left).setName(left) + Optional(expr) + Suppress(right).setName(right)
    )


ASSERT = Keyword("Assert")
TYPE = (Keyword("Type") | Keyword("Built-in Type")).setName("Type")
DATATYPE = (Keyword("Built-in Datatype") | Keyword("Datatype")).setName("Datatype")
ENTITY = Keyword("Entity")
METAVARIABLES = Keyword("Meta-variables")
ALIAS = Keyword("Alias")
RULE = Keyword("Rule")
FUNCON = (
    Keyword("Built-in Funcon") | Keyword("Auxiliary Funcon") | Keyword("Funcon")
).setName("Funcon")


KEYWORD = (ASSERT | FUNCON | TYPE | DATATYPE | ENTITY | METAVARIABLES).setName(
    "keyword"
)


IDENTIFIER = Combine(
    Word(
        initChars=alphanums + "_",
        bodyChars=alphanums + "-",
    )
    + ZeroOrMore("'")
).setName("id")

POLARITY = oneOf("! ?").setName("polarity")
SUFFIX = oneOf("* + ?").setName("suffix")
PREFIX = oneOf("~ =>").setName("prefix")
INFIX = oneOf("=> | &").setName("infix")

REWRITES_TO = Suppress("~>").setName("~>")
CONTEXTUALENTITY = Suppress("|-").setName("|-")
MAPSTO = Suppress("|->").setName("|->")
METAVARASSIGN = Suppress("<:").setName("<:")
DATATYPEASSIGN = Suppress("::=").setName("::=")
EQUALS = Suppress("==").setName("==")
NOTEQUALS = Suppress("=/=").setName("=/=")
EQUALITY = (EQUALS | NOTEQUALS).setName("equality")
COLON = Suppress(":").setName(":")
BAR = Suppress(Literal("---") + OneOrMore("-")).setName("---")
EMPTYMAP = Group(Suppress("{") + Suppress("}"))("emptymap").setName("emptymap")
BOOL = (Keyword("true") | Keyword("false")).setName("boolean")
STRING = Combine(Suppress('"') + IDENTIFIER + Literal('"')).setName("string")

# Define forward expression for recursion
expr = Forward().setName("expr")

param = Group(
    expr("value").setName("value") + Optional(COLON + expr("type").setName("type")),
)

params = encapsulate(delimitedList(param("params*")), "(", ")")

nestedexpr = encapsulate(expr, "(", ")")

fun_call = Group(IDENTIFIER("fun") + params).setName("fun_call")

mapexpr = expr("value") + MAPSTO + expr("mapsto")

mapping = encapsulate(mapexpr | delimitedList(param("params*")), "{", "}")

listexpr = encapsulate(delimitedList(param("indices*")), "[", "]")

listindex = Group(IDENTIFIER("identifier") + listexpr)

entitysig = Group(IDENTIFIER("name") + Optional(POLARITY("polarity")) + params).setName(
    "entity"
)

step = encapsulate(delimitedList(entitysig("entities*")), "--", "->").setName("step")

mutableentity = encapsulate(delimitedList(expr("mutables*")), "<", ">")

# chain = Forward()
# chain <<= Group(
#     IDENTIFIER("fun") + (chain | fun_call | IDENTIFIER)("param"),
# )("unary")

operands = Forward()

operands = Or(
    [
        listindex,
        listexpr,
        mapping,
        mutableentity,
        fun_call,
        # chain,
        nestedexpr,
        params,
        STRING,
        IDENTIFIER,
    ]
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
    + (expr("before") + step + expr("after"))
)


typeexpr = Group(expr("value") + COLON + expr("type")).setName("typeexpr")
boolexpr = Group(expr("value") + EQUALITY + (expr | BOOL | STRING | EMPTYMAP)).setName(
    "boolexpr"
)
rewrite = Group(expr("value") + REWRITES_TO + expr("rewrites_to")).setName(
    "rewriteexpr"
)
premise = Group(rewrite | boolexpr | typeexpr | contextualentity)
premises = OneOrMore(premise)

transition = premises("premises*") + BAR + premise("rewritten")

rule_def = Group(transition)("transition") | premise("premise")

rule = Suppress(RULE) + Group(rule_def)("rules*")
rules = ZeroOrMore(rule)

alias = Suppress(ALIAS) + Group(
    IDENTIFIER("alias") + Suppress("=") + IDENTIFIER("original")
)("aliases*")
aliases = ZeroOrMore(alias)

funcon_def = Group(
    IDENTIFIER("name")
    + Optional(
        params,
    )
    + COLON
    + expr("returns")
    + Optional(
        REWRITES_TO + expr("rewrites_to"),
    )
)("definition")
funcon_parser = Suppress(FUNCON) + funcon_def + aliases + rules

entitydef = contextualentity | mutableentity
entity_parser = Suppress(ENTITY) + entitydef + aliases

type_def = (
    expr("type")
    + Optional(METAVARASSIGN + expr("assigns"))
    + Optional(REWRITES_TO + expr("value"))
)
type_parser = Suppress(TYPE) + type_def + aliases

datatypedef = (
    expr("name") + Suppress(DATATYPEASSIGN | METAVARASSIGN) + expr("definition")
)
datatype_parser = Suppress(DATATYPE) + datatypedef + aliases

metavar_type = expr("types*")
metavar_types = delimitedList(metavar_type)
metavar_def = metavar_types + METAVARASSIGN + expr("definition")
metavar_defs = OneOrMore(metavar_def)
metavar_parser = Suppress(METAVARIABLES) + metavar_defs

assert_def = expr("expr") + Suppress(EQUALS) + expr("equals")
assert_parser = Suppress(ASSERT) + assert_def


parsers = {
    "Entity": entity_parser,
    "Meta-variables": metavar_parser,
    "Type": type_parser,
    "Datatype": datatype_parser,
    "Funcon": funcon_parser,
    "Assert": assert_parser,
}

componentparser = ZeroOrMore(
    Combine(KEYWORD + ... + Suppress(FollowedBy(KEYWORD | StringEnd())), "\n")
)


def clean_text(string):
    indexline = KEYWORD + IDENTIFIER + Optional(ALIAS + IDENTIFIER)
    indexlines = OneOrMore(indexline)

    index = Suppress("[" + indexlines + "]")

    multiline_comment = Suppress(Literal("/*") + ... + Literal("*/"))

    header = Suppress(OneOrMore("#") + restOfLine)

    remove = multiline_comment | index | header

    return remove.transformString(string).strip()



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
        strings = componentparser.parseString(cleaned, parseAll=True)
    for component in strings:
        splitline = component.split()
        keyword = splitline[0]
        if keyword in ["Auxiliary", "Built-in"]:
            keyword = splitline[1]
        components[keyword].append(component)
    return components


def parse_file(path, dump_json=False, print_res=False):
    data = defaultdict(lambda: [])
    file_components = parse_file_components(path)
    for keyword, components in file_components.items():
        for component in components:
            res = parse_str(parsers[keyword], component, print_res)
            data[keyword].append(res.asDict())
    if print_res:
        pprint(data)
    if dump_json:
        json.dump(data)
