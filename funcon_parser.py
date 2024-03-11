import glob
import os

from icecream import ic
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

FUNCON_NAME = Word(alphas, alphanums + "-")
IDENTIFIER = Word(alphas, alphanums + "-'") | "_"
MODIFIER = oneOf("* + ?")

COMPUTES = "=>"
REWRITES_TO = "~>"


# Temp name
inner_type = (
    Optional(COMPUTES)
    + (IDENTIFIER | (Optional(Suppress("(")) + IDENTIFIER + Optional(Suppress(")"))))
    + Optional(MODIFIER)
)
type = Group(
    (inner_type | (Optional(Suppress("(")) + inner_type + Optional(Suppress(")"))))
    + Optional(MODIFIER),
)

value = Group(
    IDENTIFIER + Optional(MODIFIER),
)


def funcon_rule_parser():
    args = Group(delimitedList(IDENTIFIER + Optional(MODIFIER))).setResultsName("args*")
    entity = Group(
        Suppress("--")
        + IDENTIFIER
        + Optional(MODIFIER)
        + Suppress("(")
        + value.setResultsName("value")
        + Optional(Suppress(":") + type.setResultsName("type"))
        + Suppress(")")
    ).setResultsName("entity")
    rewrite = Group(
        IDENTIFIER
        + Suppress("(")
        + args
        + Suppress(")")
        + Optional(entity)
        + REWRITES_TO
        + IDENTIFIER,
    ).setResultsName("rules*")

    rule = RULE + rewrite

    return rule


def funcon_def_parser():
    values = delimitedList(
        Group(
            value.setResultsName("value") + Suppress(":") + type.setResultsName("type"),
        ).setResultsName("values*")
    )
    alias = ALIAS + Group(
        IDENTIFIER + "=" + IDENTIFIER,
    ).setResultsName("aliases*")

    rule = funcon_rule_parser()

    funcon = FUNCON + Group(
        FUNCON_NAME
        + Suppress("(")
        + values
        + Suppress(")")
        + Suppress(":")
        + type.setResultsName("returns")
    ).setResultsName("funcon")
    funcon_definition = Group(
        funcon + ZeroOrMore(alias | rule),
    ).setResultsName("funcon_definitions*")
    return funcon_definition


def entity_parser():
    pass


def type_parser():
    pass


def metavariables_parser():
    return METAVARIABLES + OneOrMore(
        Group(
            delimitedList(type.setResultsName("types*"))
            + Suppress("<:")
            + Group(IDENTIFIER + Optional(MODIFIER)).setResultsName("varname"),
        ).setResultsName("metavariables*")
    )


def build_parser():
    file = Group(
        Suppress("###") + IDENTIFIER,
    ).setResultsName("filename")
    section = Group(
        Suppress("####") + IDENTIFIER,
    )
    index_line = Group(KEYWORD + IDENTIFIER + Optional(ALIAS + IDENTIFIER))
    index = Group(
        Suppress("[") + OneOrMore(index_line) + Suppress("]"),
    ).setResultsName("index")
    multiline_comment = Suppress("/*") + ... + Suppress("*/")
    metavariables = metavariables_parser()
    funcon_definitions = funcon_def_parser()

    parser = OneOrMore(
        file | Suppress(index) | Suppress(section) | metavariables | funcon_definitions
    )
    return parser.ignore(multiline_comment)


def parse_file(file):
    try:
        parser = build_parser()
        result = parser.parseFile(file)
        ic(result.asList())
    except ParseException as e:
        ic(ParseException.explain(e))


def main():
    # funcondir = "/home/rick/workspace/thesis/CBS-beta/Funcons-beta/Computations/Normal"
    # pattern = os.path.join(funcondir, "**/*.cbs")
    # cbs_files = glob.glob(pattern, recursive=True)
    # for path in cbs_files:
    #     print(path)
    #     with open(path) as file:
    #         parse_file(file)
    #     print()
    with open(
        "/home/rick/workspace/thesis/CBS-beta/Funcons-beta/Computations/Normal/test.cbs"
    ) as f:
        parse_file(f)


if __name__ == "__main__":
    main()
