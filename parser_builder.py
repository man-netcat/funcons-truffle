import json
from collections import defaultdict
from pprint import pprint

from pyparsing import (
    Combine,
    Forward,
    Group,
    Keyword,
    Literal,
    OneOrMore,
    Optional,
    Or,
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


def encapsulate(expr, left, right):
    return (
        Suppress(left).setName(left) + Optional(expr) + Suppress(right).setName(right)
    )


ASSERT = Keyword("Assert")
TYPE = (Keyword("Type") | Keyword("Built-in Type")).setName("Type")
DATATYPE = (Keyword("Datatype") | Keyword("Built-in Datatype")).setName("Datatype")
ENTITY = Keyword("Entity")
METAVARIABLES = Keyword("Meta-variables")
ALIAS = Keyword("Alias")
RULE = Keyword("Rule")
FUNCON = (
    Keyword("Funcon") | Keyword("Built-in Funcon") | Keyword("Auxiliary Funcon")
).setName("Funcon")


KEYWORD = (
    ASSERT | FUNCON | TYPE | DATATYPE | ENTITY | METAVARIABLES | ALIAS | RULE
).setName("keyword")


IDENTIFIER = Combine(
    Word(
        initChars=alphanums + "_",
        bodyChars=alphanums + "-",
    )
    + ZeroOrMore("'")
).setName("identifier")

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
        params,
        fun_call,
        # chain,
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


typeexpr = (expr("value") + COLON + expr("type")).setName("typeexpr")
boolexpr = (expr("value") + EQUALITY + (expr | BOOL | STRING | EMPTYMAP)).setName(
    "boolexpr"
)
rewrite = (expr("value") + REWRITES_TO + expr("rewrites_to")).setName("rewriteexpr")
premise = rewrite | boolexpr | typeexpr | contextualentity

transition = OneOrMore(premise)("premises*") + BAR + premise("rewritten")

rule_def = transition | premise

funcon_rule_parser = Group(Suppress(RULE) + rule_def)("rules*")

alias_parser = Group(
    Suppress(ALIAS) + IDENTIFIER("alias") + Suppress("=") + IDENTIFIER("original"),
)("aliases*")


funcon_def_parser = Group(
    Suppress(FUNCON)
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

funcon_parser = Group(
    funcon_def_parser + ZeroOrMore(alias_parser) + ZeroOrMore(funcon_rule_parser),
)("funcons*")

entity_parser = Group(
    Suppress(ENTITY) + (contextualentity | mutableentity),
)("entities*")

typedef = (
    expr("type")
    + Optional(METAVARASSIGN + expr("assigns"))
    + Optional(REWRITES_TO + expr("value"))
)

type_def_parser = Group(
    Suppress(TYPE) + typedef + ZeroOrMore(alias_parser("aliases*")),
)("types*")

datatype_parser = Group(
    Suppress(DATATYPE)
    + expr("name")
    + Suppress(DATATYPEASSIGN | METAVARASSIGN)
    + expr("definition")
)("datatypes*")

var = infixNotation(
    fun_call | IDENTIFIER,
    [(SUFFIX, 1, opAssoc.LEFT)],
)

metavardef = Group(delimitedList(var("types*"))) + METAVARASSIGN + var("definition")

metavariables_parser = Suppress(METAVARIABLES) + OneOrMore(metavardef("metavariables*"))

assert_parser = Group(
    Suppress(ASSERT) + expr("expr") + Suppress(EQUALS) + expr("equals"),
)("assertions*")


parsers = {
    "Entity": entity_parser,
    "Meta-variables": metavariables_parser,
    "Type": type_def_parser,
    "Alias": alias_parser,
    "Datatype": datatype_parser,
    "Funcon": funcon_parser,
    "Rule": funcon_rule_parser,
    "Assert": assert_parser,
}

indented_line = Suppress(White(min=2)) + restOfLine
indented_lines = OneOrMore(indented_line, stopOn=KEYWORD)

funcon_component = (
    (FUNCON + restOfLine + indented_lines)
    + OneOrMore(ALIAS + restOfLine + indented_lines)
    + OneOrMore(RULE + restOfLine + indented_lines)
)
type_component = (TYPE + restOfLine + indented_lines) + OneOrMore(
    ALIAS + restOfLine + indented_lines
)
misc_component = KEYWORD + restOfLine + indented_lines

component = Combine(funcon_component | type_component | misc_component, "\n")

component_parser = ZeroOrMore(component)


def clean_text(string):
    indexlines = KEYWORD + IDENTIFIER + Optional(ALIAS + IDENTIFIER)

    index = Suppress("[" + OneOrMore(indexlines) + "]")

    multiline_comment = Suppress(Literal("/*") + ... + Literal("*/"))

    header = Suppress(OneOrMore("#") + restOfLine)

    remove = multiline_comment | index | header
    return "".join(remove.transformString(string))


def parse_str(parser, str, print_res=False):
    try:
        res = parser.parseString(str, parseAll=True)
        if print_res:
            pprint(res.asDict())
        return res
    except ParseException as e:
        e: ParseException
        sep = "~" * 70
        errstr = f"\n{sep}\n{e.args[0]}\n{sep}\n{ParseException.explain(e)}"
        raise ParseException(errstr) from None
    except RecursionError as e:
        raise ParseException("Recursionerror :(") from None


def parse_file_components(path) -> dict:
    cases = defaultdict(lambda: [])
    with open(path, "r") as file:
        text = file.read()
        try:
            cleaned = clean_text(text)
            res = component_parser.parseString(cleaned, parseAll=True)
        except ParseException as e:
            e: ParseException
            sep = "~" * 70
            errstr = f"\n{sep}\n{e.args[0]}\n{sep}\n{ParseException.explain(e)}"
            raise ParseException(errstr) from None
        except RecursionError as e:
            raise ParseException("Recursionerror :(") from None
    for line in res:
        splitline = line.split()
        keyword = splitline[0]
        if keyword in ["Auxiliary", "Built-in"]:
            keyword = splitline[1]
        cases[keyword].append(line)
    return cases


def parse_file(path, dump_json=False, print_res=False):
    data = defaultdict(lambda: [])
    typecases = parse_file_components(path)
    for keyword, cases in typecases.items():
        for case in cases:
            res = parse_str(parsers[keyword], case, print_res)

    if print_res:
        print(data.asDict())
    if dump_json:
        json.dump(data)
