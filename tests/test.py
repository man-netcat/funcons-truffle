from unittest import TestCase, main

from funcon_parser import (
    entity_parser,
    funcon_alias_parser,
    funcon_def_parser,
    funcon_rule_parser,
    datatype_parser,
)
from pyparsing import ParseException


def parse_with_parser(parser_func, strings):
    for string in strings:
        try:
            parser_func().parseString(string)
        except ParseException as e:
            raise ParseException("\n" + ParseException.explain(e)) from None


class TestPyParsingRules(TestCase):
    def test_funcon_parser(self):
        strs = [
            "Funcon print(_:values*) : =>null-type",
            "Funcon left-to-right(_:(=>(T)*)*) : =>(T)*",
            "Funcon right-to-left(_:(=>(T)*)*) : =>(T)*",
            "Funcon sequential(_:(=>null-type)*, _:=>T) : =>T",
            "Funcon effect(V*:T*) : =>null-type ~> null-value",
            "Funcon choice(_:(=>T)+) : =>T",
            "Funcon yield : =>null-type ~> yield-on-value(null-value)",
        ]
        parse_with_parser(funcon_def_parser, strs)

    def test_funcon_rule(self):
        strs = ["Rule atomic(V:T) ~> V"]
        parse_with_parser(funcon_rule_parser, strs)

    def test_funcon_alias(self):
        strs = ["Alias env = environment"]
        parse_with_parser(funcon_alias_parser, strs)

    def test_entity(self):
        strs = ["Entity _ --yielded(_:yielding?)-> _"]
        parse_with_parser(entity_parser, strs)

    def test_datatype(self):
        strs = ["Datatype yielding ::= signal"]
        parse_with_parser(datatype_parser, strs)


if __name__ == "__main__":
    main()
