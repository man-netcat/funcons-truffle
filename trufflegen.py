import argparse
import glob
import os
from dataclasses import dataclass
from enum import Enum, Flag, auto
from pathlib import Path
from typing import Literal

from icecream.icecream import ic
from parser_builder import parse_cbs_file

COMPUTATION = "Computation"
TERMINAL = "Terminal"
CBS_NODE = "CBSNode"
MAIN = "main"
GENERATED = "generated"
TRUFFLEGEN = "trufflegen"


class Unimplemented(Exception):
    def __init__(self, message, errors):
        super().__init__(message)


ic.configureOutput(includeContext=True)


def node_name(name):
    return "".join(w.capitalize() for w in str(name).split("-")) + "Node"


def make_body(str):
    return "{\n" + "\n".join(["    " + line for line in str.splitlines()]) + "\n}"


def if_else_chain(ifstmts):
    return " else ".join([stmt for stmt in ifstmts if stmt])


def if_stmt(condition, body):
    if not condition:
        return body
    return f"if ({condition}) {make_body(body)}"


class VarType(Flag):
    NORMAL = 0
    STAR = 1
    PLUS = 2
    OPT = 3


class TypeAttribute(Enum):
    VARARG = 0
    LAZY = 1


class ParamType(Enum):
    FUNCON_PARAM = 0
    RULE_PARAM = 1


def value_type(value):
    match value:
        case [value, "*"]:
            return VarType.STAR
        case [value, "+"]:
            return VarType.PLUS
        case [value, "?"]:
            return VarType.OPT
        case value:
            return VarType.NORMAL


class Metavar:
    def __init__(self, data, filename: str) -> None:
        self.filename = filename
        self.name = "".join(data["definition"])
        self.data = data
        self.definition = data["definition"]
        self.types = [type[0] if len(type) == 1 else type for type in data["types"]]


class Datatype:
    def __init__(self, data, filename: str) -> None:
        self.filename = filename
        self.data = data
        self.name = data["name"]
        self.definition = data["definition"]

    @property
    def class_signature(self):
        return (
            f"class {node_name(self.name)}(override val value: String) : {TERMINAL}()"
        )

    @property
    def body(self):
        return make_body(f"override fun execute(frame: VirtualFrame): String = value")

    @property
    def code(self):
        node_info = f'@NodeInfo(shortName = "{self.name}")'
        return f"{node_info}\n{self.class_signature} {self.body}"


class Param:
    def __init__(self, data, i) -> None:
        self.data = data
        self.value = data["value"]
        self.type = ValueType(data.get("type"))
        self.idx = i
        self.value_type = value_type(self.value)

    def __repr__(self) -> str:
        return str(self.data)

    def __str__(self) -> str:
        return f"{self.value}: {self.type}"

    @property
    def param_str(self):
        returntype = f"Array<{CBS_NODE}>" if self.type.is_array else CBS_NODE
        if self.type.is_vararg:
            return f"@Children private vararg val p{self.idx}: {returntype}"
        else:
            return f"@Child private var p{self.idx}: {returntype}"


class ParamContainer:
    def __init__(self, params, param_type, funcon) -> None:
        if params is None:
            self.params = []
        else:
            self.params = [Param(param, i) for i, param in enumerate(params)]

        self.param_type = param_type
        self.funcon = funcon

    def __contains__(self, value):
        return value in (param.value for param in self.params)

    def __len__(self):
        return len(self.params)

    def __iter__(self):
        for param in self.params:
            yield param

    def __str__(self) -> str:
        return (
            "\n    " + ",\n    ".join([param.param_str for param in self.params]) + "\n"
        )

    def __repr__(self) -> str:
        return ", ".join([param.param_str for param in self.params])

    def __getitem__(self, value):
        if isinstance(value, int):
            param = self.params[value]
        elif isinstance(value, str):
            param = next(
                (param for param in self.params if str(param.value) == value), None
            )
        return param


class ValueType:
    def __init__(self, data):
        self.data = data
        self.is_final_arg = False

        if data is None:
            self.value = None
            self.is_vararg = False
            self.is_array = False
            self.is_lazy = False
            return

        self.value, type_attributes = self.get_type_attributes(data)

        vararg_count = type_attributes.count(TypeAttribute.VARARG)
        lazy_count = type_attributes.count(TypeAttribute.LAZY)

        assert vararg_count <= 2 or lazy_count <= 1

        self.is_vararg = vararg_count > 0
        self.is_array = vararg_count == 2
        self.is_lazy = lazy_count == 1

    def __str__(self) -> str:
        if self.data == None:
            return "typeless"

        if self.is_array:
            return f"Array<{node_name(self.value)}>"
        else:
            return node_name(self.value)

    def get_type_attributes(self, type, attributes=None):
        if attributes is None:
            attributes = []
        match type:
            case [t, "*" | "+" | "?"]:
                attributes.append(TypeAttribute.VARARG)
                return self.get_type_attributes(t, attributes)
            case ["=>", t]:
                attributes.append(TypeAttribute.LAZY)
                return self.get_type_attributes(t, attributes)
            case t:
                return t, attributes


class RuleCategory(Enum):
    TERMREWRITE = auto()
    PREMISECONCLUSION = auto()
    TYPECONDITION = auto()
    EQUALS = auto()
    NOTEQUALS = auto()


class Rule:
    def __init__(self, data, funcon: "Funcon") -> None:
        self.data = data

        match data:
            case {"term": term, "rewrites_to": rewrites_to}:
                self.term = term
                match self.term:
                    case {"fun": fun, "params": params}:
                        self.node = node_name(fun)
                        self.params = ParamContainer(
                            params,
                            ParamType.RULE_PARAM,
                            funcon,
                        )
                        self.funcon = funcon
                        self.has_two_varargs = (
                            sum([param.value_type.value > 0 for param in self.params])
                            == 2
                        )
                    case value:
                        self.value = value
                self.rewrites_to = rewrites_to
                self.category = RuleCategory.TERMREWRITE
            case {"premises": premises, "conclusion": conclusion}:
                self.premises = [Rule(premise, funcon) for premise in premises]
                self.conclusion = Rule(conclusion, funcon)
                self.category = RuleCategory.PREMISECONCLUSION
            case {"value": value, "type": type}:
                self.value = value
                self.type = type
                self.category = RuleCategory.TYPECONDITION
            case {"value": value, "equals": equals}:
                self.value = value
                self.equals = equals
                self.category = RuleCategory.EQUALS
            case {"value": value, "notequals": notequals}:
                self.value = value
                self.notequals = notequals
                self.category = RuleCategory.NOTEQUALS
            case _:
                raise Unimplemented("Unsupported rule")

    def __str__(self) -> str:
        return str(self.data)


class ConditionType(Enum):
    EMPTY = 0
    SINGLE = 1
    CONTEXTFREEREWRITE = 2
    TRANSITION = 3
    RANDOM = 4


class Funcon:
    def __init__(self, data, filename: str) -> None:
        self.filename = filename
        self.filedata = globaldata.filedata[self.filename]
        self.data = data
        self.definition = self.data["definition"]
        self.name = self.definition["name"]

        self.params = ParamContainer(
            self.definition.get("params"),
            ParamType.FUNCON_PARAM,
            self,
        )
        self.n_params = len(self.params)

        assert sum([param.type.is_vararg for param in self.params]) <= 1

        self.has_varargs = any(param.type.is_vararg for param in self.params)

        if self.has_varargs:
            self.vararg_index = next(
                (i for i, param in enumerate(self.params) if param.type.is_vararg)
            )
            self.n_regular_args = self.vararg_index
            self.n_final_args = self.n_params - self.vararg_index - 1
            if self.n_final_args > 0:
                for i in range(1, self.n_final_args + 1):
                    self.params[-i].type.is_final_arg = True

        else:
            self.n_final_args = 0
            self.n_regular_args = self.n_params

        if "rewrites_to" in self.definition:
            self.rewrites_to = self.definition["rewrites_to"]
            self.rules = None
        else:
            self.rules = [Rule(rule, self) for rule in self.data.get("rules", [])]
            self.rewrites_to = None

        self.return_type = ValueType(self.definition["returns"])

        self.signature = (
            f"class {node_name(self.name)}({self.params}) : {COMPUTATION}()"
        )
        self.executed = dict()
        self.n_executed = 0

    def get_index(self, index: int, n_params: int):
        n_varargs = n_params - (self.n_regular_args + self.n_final_args)

        if index < self.n_regular_args:
            return index
        elif index < self.n_regular_args + n_varargs:
            return (self.vararg_index, index - self.vararg_index)
        elif index < self.n_regular_args + n_varargs + self.n_final_args:
            return index - n_varargs + 1
        else:
            raise ValueError("Invalid argument values provided")

    def make_param_str(self, param_idx, n_term_params):
        fparam_idx = self.get_index(param_idx, n_term_params)
        match fparam_idx:
            case (param_index, vararg_index):
                return f"p{param_index}[{vararg_index}]"
            case param_index:
                return f"p{param_index}"

    def rewrite_expr(self, expr, term_params: ParamContainer):
        def recursive_call(expr, funcon_name=None):
            match expr:
                case {"fun": funcon_name, "params": params}:
                    strs = [
                        recursive_call(param["value"], funcon_name=funcon_name)
                        for param in params
                    ]
                    funcon: Funcon = globaldata.getattr("funcons")[funcon_name]
                    if funcon.n_final_args > 0 and not self.n_final_args > 0:
                        for i, param in zip(
                            range(1, funcon.n_final_args + 1), reversed(funcon.params)
                        ):
                            strs[-i] = f"p{param.idx}={strs[-i]}"

                    param_str = ", ".join(strs)
                    rewrite_str = f"{node_name(funcon_name)}({param_str})"
                    if funcon_name in globaldata.getattr("funcons"):
                        rewrite_str += ".execute(frame)"
                    return rewrite_str
                case value:
                    n_term_params = len(term_params)
                    param = term_params[str(value)]

                    if param is None:
                        return f'{self.return_type}("{value}")'

                    if param.value_type.value > 0:
                        star = "*" if funcon_name else ""
                        if self.n_final_args == 0:
                            if param.idx == 0:
                                return f"{star}p{self.vararg_index}"
                            else:
                                return f"{star}slice(p{self.vararg_index}, {param.idx})"
                        else:
                            vararg_part = f"{star}slice(p{self.vararg_index}, {param.idx}, {self.n_final_args})"
                            final_part = ", ".join(
                                [
                                    f"p{i}=p{i}"
                                    for i in range(
                                        self.vararg_index + 1,
                                        self.vararg_index + self.n_final_args + 1,
                                    )
                                ]
                            )
                            return f"{vararg_part}, {final_part}"
                    else:
                        return self.make_param_str(param.idx, n_term_params)

        return recursive_call(expr)

    def f_param(self, rule_param: Param, rule: Rule):
        f_param_idx = self.get_index(rule_param.idx, len(rule.params))

        f_param = self.params[
            (f_param_idx if isinstance(f_param_idx, int) else f_param_idx[0])
        ]
        return f_param

    def ex_param(self, param_str):
        ex_param = self.executed.get(param_str)
        if ex_param is None:
            ex_param = f"p{self.n_executed}.execute(frame) as {CBS_NODE}"
            self.executed[param_str] = ex_param
            self.n_executed += 1
        return ex_param

    def node_body(self):
        print()
        print(self.name)
        f_param_strs = [
            f"{self.make_param_str(param.idx, len(self.params))}"
            for param in self.params
            if not param.type.is_lazy
        ]
        kt_rules = []
        for rule in self.rules:
            if rule.category == RuleCategory.TERMREWRITE:
                rule_conditions = []

                if len(rule.params) == 0 or len(rule.params) == self.n_final_args:
                    kt_condition = f"p{self.vararg_index}.isEmpty()"
                    conditiontype = ConditionType.EMPTY
                elif (
                    len(rule.params) == 1
                    # and not self.f_param(rule.params[0], rule).type.is_array
                    and rule.params[0].value == rule.rewrites_to
                ):
                    if self.has_varargs:
                        kt_condition = f"p{self.vararg_index}.size == 1"
                    else:
                        kt_condition = ""
                    conditiontype = ConditionType.SINGLE
                else:
                    # ic(rule.data)
                    for rule_param in rule.params:
                        if rule_param.type.is_vararg:
                            continue
                        param_str = self.make_param_str(
                            rule_param.idx, len(rule.params)
                        )
                        f_param = self.f_param(rule_param, rule)
                        if not f_param.type.value in self.filedata.get_metavar_types():
                            condition = (
                                f'{param_str}.execute(frame) == "{rule_param.value}"'
                            )
                            rule_conditions.append(condition)

                    kt_condition = " && ".join(rule_conditions)
                    conditiontype = ConditionType.CONTEXTFREEREWRITE
                if rule.has_two_varargs:
                    kt_returns = f"p{self.vararg_index}.random()"
                    kt_rule = (conditiontype, kt_condition, kt_returns)
                else:
                    kt_returns = self.rewrite_expr(rule.rewrites_to, rule.params)
                    if kt_returns is None:
                        kt_returns = f'{self.return_type}("{rule.rewrites_to}")'
                    kt_rule = (conditiontype, kt_condition, kt_returns)
                kt_rules.append(kt_rule)
            elif RuleCategory.PREMISECONCLUSION:
                ic(rule.data)
                param_strs = []
                for premise in rule.premises:
                    if premise.category == RuleCategory.TERMREWRITE:
                        param_str = self.rewrite_expr(
                            premise.term, rule.conclusion.params
                        )
                        param_strs.append(param_str)
                        kt_condition = " && ".join(
                            [f"{param_str}.isComputation()" for param_str in param_strs]
                        )
                    # TODO
                    # elif premise.category == RuleCategory.TYPECONDITION:
                    #     ic(premise.value, premise.type)
                    #     pass
                    # elif premise.category == RuleCategory.EQUALS:
                    #     ic(premise.value, premise.equals)
                    #     pass
                    # elif premise.category == RuleCategory.NOTEQUALS:
                    #     ic(premise.value, premise.notequals)
                    #     pass
                kt_returns = self.rewrite_expr(
                    rule.conclusion.rewrites_to, rule.conclusion.params
                )
                kt_rule = (ConditionType.TRANSITION, kt_condition, kt_returns)
                kt_rules.append(kt_rule)

        ifs = [
            if_stmt(rule[1], "return " + rule[2]) if rule[1] else rule[2]
            for rule in [
                rule
                for rule in kt_rules
                if rule[0] in [ConditionType.EMPTY, ConditionType.SINGLE]
            ]
        ]

        rewrite = if_else_chain(
            [
                if_stmt(rule[1], f"return {rule[2]}")
                for rule in [
                    rule
                    for rule in kt_rules
                    if rule[0] in [ConditionType.CONTEXTFREEREWRITE]
                ]
            ]
        )
        termcondition = " && ".join(
            [f"{f_param}.isTerminal()" for f_param in f_param_strs]
        )
        terminal = if_stmt(termcondition, rewrite)
        ifs.append(terminal)

        for f_param in f_param_strs:
            compcondition = f"{f_param}.isComputation()"
            param_strs = []
            for param in rule.params:
                param_str = self.make_param_str(param.idx, len(rule.params))
                if param_str == f_param:
                    param_str += ".execute(frame) as CBSNode"
                elif param.value_type.value > 0:
                    if self.n_final_args == 0:
                        param_str = f"*slice(p{self.vararg_index}, {param.idx})"
                    else:
                        vararg_part = f"*slice(p{self.vararg_index}, {param.idx}, {self.n_final_args})"
                        final_part = ", ".join(
                            [
                                f"p{i}=p{i}"
                                for i in range(
                                    self.vararg_index + 1,
                                    self.vararg_index + self.n_final_args + 1,
                                )
                            ]
                        )
                        return f"{vararg_part}, {final_part}"
                param_strs.append(param_str)
            node_params = ", ".join(param_strs)

            body = f"{node_name(self.name)}({node_params}).execute(frame)"
            computation = if_stmt(compcondition, "return " + body)
            ifs.append(computation)

        body = if_else_chain(ifs)

        body += "\nthrow RuntimeException()"

        return body

    @property
    def rule_body(self):
        body = self.node_body()

        if self.return_type.is_vararg:
            returns = f"Array<out {CBS_NODE}>"
        else:
            returns = CBS_NODE

        return f"override fun execute(frame: VirtualFrame): {returns} {make_body(body)}"

    @property
    def rewrite_body(self):
        kt_return = f"return {self.rewrite_expr(self.rewrites_to, self.params)}"
        body = f"override fun execute(frame: VirtualFrame): {CBS_NODE} {make_body(kt_return)}"
        return body

    @property
    def body(self):
        if self.rewrites_to:
            body = self.rewrite_body
        else:
            body = self.rule_body

        return make_body(body)

    @property
    def code(self):
        node_info = f'@NodeInfo(shortName = "{self.name}")'
        return f"{node_info}\n{self.signature} {self.body}"


@dataclass
class FileData:
    def __init__(self):
        self.metavars: dict[str, Metavar] = {}
        self.datatypes: dict[str, Datatype] = {}
        self.funcons: dict[str, Funcon] = {}

    def get_metavar_types(self):
        return [type for metavar in self.metavars.values() for type in metavar.types]


@dataclass
class GlobalData:
    def __init__(self):
        self.filedata: dict[str, "FileData"] = {}

    def getattr(
        self, attribute: Literal["datatypes", "funcons", "metavars"]
    ) -> dict[str]:
        return {
            name: obj
            for file in self.filedata.values()
            for name, obj in getattr(file, attribute).items()
        }


class CodeGen:

    def __init__(self, cbs_dir) -> None:
        pattern = os.path.join(cbs_dir, "**/*.cbs")
        self.cbs_files = glob.glob(pattern, recursive=True)

        for path in self.cbs_files:
            filename = Path(path).stem
            globaldata.filedata[filename] = FileData()

            ast = parse_cbs_file(path, dump_json=False).asDict()
            for cls, value in zip(
                [Datatype, Funcon, Metavar],
                ["datatypes", "funcons", "metavars"],
            ):
                if value in ast:
                    for value_ast in ast[value]:
                        obj = cls(value_ast, filename)
                        getattr(globaldata.filedata[filename], value)[obj.name] = obj

    def generate(self):
        for path in self.cbs_files:
            if not any(
                x in path
                for x in [
                    # "Flowing",
                    "Booleans",
                    "Null",
                    # "Linking",
                    # "Storing",
                ]
            ):
                continue
            filename = Path(path).stem

            truffle_api_imports = "\n".join(
                [
                    f"package {TRUFFLEGEN}.{GENERATED}\n",
                    f"import {TRUFFLEGEN}.{MAIN}.*",
                ]
                + [
                    f"import com.oracle.truffle.api.{i}"
                    for i in [
                        "frame.VirtualFrame",
                        "nodes.NodeInfo",
                    ]
                ]
            )

            code = "\n\n".join(
                [
                    obj.code
                    for x in ["datatypes", "funcons"]
                    for obj in getattr(globaldata.filedata[filename], x).values()
                ]
            )

            code = truffle_api_imports + "\n\n" + code

            kt_path = (
                f"kt_source/src/main/kotlin/{TRUFFLEGEN}/{GENERATED}/{filename}.kt"
            )
            with open(kt_path, "w") as f:
                f.write(code)
            print(f"Written to {kt_path}\n")


def main():
    parser = argparse.ArgumentParser(description="Generate code from CBS file")
    parser.add_argument(
        "cbs_dir",
        help="Generate kotlin files for all .cbs files in specified direcotry",
    )
    args = parser.parse_args()
    generator = CodeGen(args.cbs_dir)
    generator.generate()


if __name__ == "__main__":
    globaldata = GlobalData()
    main()
