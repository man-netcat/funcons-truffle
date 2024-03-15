from unittest import TestCase, main

from funcon_parser import (
    datatype_parser,
    entity_parser,
    funcon_alias_parser,
    funcon_def_parser,
    funcon_rule_parser,
)
from pyparsing import ParseException

test_cases = {
    funcon_def_parser: [
        "Funcon print(_:values*) : =>null-type",
        "Funcon left-to-right(_:(=>(T)*)*) : =>(T)*",
        "Funcon right-to-left(_:(=>(T)*)*) : =>(T)*",
        "Funcon sequential(_:(=>null-type)*, _:=>T) : =>T",
        "Funcon effect(V*:T*) : =>null-type ~> null-value",
        "Funcon choice(_:(=>T)+) : =>T",
        "Funcon yield : =>null-type ~> yield-on-value(null-value)",
        "Funcon stuck : =>empty-type",
    ],
    funcon_rule_parser: [
        "Rule atomic(V:T) ~> V",
        "Rule Y ---> Y' ---- left-to-right(V*:(T)*, Y, Z*) ---> left-to-right(V*, Y', Z*)",
        "Rule force(thunk(abstraction(X))) ~> no-given(X)",
    ],
    funcon_alias_parser: [
        "Alias env = environment",
    ],
    entity_parser: [
        "Entity < _ , used-atom-set(_:sets(atoms)) > ---> < _ , used-atom-set(_:sets(atoms)) >",
        "Entity _ --yielded(_:yielding?)-> _",
    ],
    datatype_parser: [
        "Datatype yielding ::= signal",
        "Datatype thunks(T) ::= thunk(_:abstractions(()=>T))",
    ],
}


class TestParserFunction(TestCase):
    pass


# Dynamically generate test functions for each test case
for func, cases in test_cases.items():

    for i, case in enumerate(cases, 1):
        print(func, case)

        def generate_test_function(func, case):
            def test_function(_):
                try:
                    func().parseString(case)
                except ParseException as e:
                    errstr = f"\n{'-'*70}\n{ParseException.explain(e)}"
                    raise ParseException(errstr) from None

            return test_function

        test_func = generate_test_function(func, case)
        setattr(TestParserFunction, f"test_{func.__name__}_{i}", test_func)


if __name__ == "__main__":
    main()
