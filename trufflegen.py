import argparse
from dataclasses import dataclass
import glob
import os
from enum import Enum, Flag
from pathlib import Path

from icecream.icecream import ic
from parser_builder import parse_cbs_file

FUNCON_NODE = "FunconNode"
DATATYPE_NODE = "DataTypeNode"
CBS_NODE = "CBSNode"

ic.configureOutput(includeContext=True)


def node_name(name):
    return "".join(w.capitalize() for w in str(name).split("-")) + "Node"


def make_body(str):
    return "{\n" + "\n".join(["    " + line for line in str.splitlines()]) + "\n}"


def recursive_call(expr, value_fun, *args):
    match expr:
        case {"fun": fun, "params": params}:
            param_str = ", ".join(
                [recursive_call(param["value"], value_fun) for param in params]
            )
            return f"{node_name(fun)}({param_str})"
        case value:
            return value_fun(value, *args)


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
    def __init__(self, data) -> None:
        pass


class Datatype:
    def __init__(self, data) -> None:
        self.name = data["name"]
        self.def_ = data["definition"]

    @property
    def class_signature(self):
        return f"class {node_name(self.name)}(private val value: String) : {DATATYPE_NODE}()"

    @property
    def code(self):
        return " ".join(
            [
                self.class_signature,
                make_body(f"override fun execute(frame: VirtualFrame): String = value"),
            ]
        )


class Param:
    def __init__(self, data, i) -> None:
        self.data = data
        self.value = data["value"]
        self.type = Type(data.get("type"))
        self.idx = i
        self.value_type = value_type(self.value)

    def __repr__(self) -> str:
        return str(self.data)

    def __str__(self) -> str:
        return f"{self.value}: {self.type}"

    @property
    def param_str(self):
        return f"    @Child private {'vararg ' if self.type.is_vararg else ''}val p{self.idx}: {CBS_NODE}"


class ParamContainer:
    def __init__(self, params, param_type) -> None:
        if params is None:
            self.params = []
        else:
            self.params = [Param(param, i) for i, param in enumerate(params)]

        self.param_type = param_type

    def __contains__(self, value):
        return value in (param.value for param in self.params)

    def __len__(self):
        return len(self.params)

    def __iter__(self):
        for param in self.params:
            yield param

    def __str__(self) -> str:
        return "\n" + ",\n".join([param.param_str for param in self.params]) + "\n"

    def __repr__(self) -> str:
        return str(self)

    def __getitem__(self, value):
        if isinstance(value, int):
            param = self.params[value]
        elif isinstance(value, str):
            param = next(
                (param for param in self.params if str(param.value) == value), None
            )
        return param


class Type:
    def __init__(self, data):
        self.data = data

        if data is None:
            self.type = None
            self.is_vararg = False
            self.is_array = False
            self.is_lazy = False
            return

        self.type, type_attributes = self.get_type_attributes(data)

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
            return f"Array<{node_name(self.type)}>"
        else:
            return f"{node_name(self.type)}"

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


class Rule:
    def __init__(self, data, funcon: "Funcon") -> None:
        self.data = data

        match data:
            case {"term": term, "rewrites_to": rewrites_to}:
                self.term = term
                match self.term:
                    case {"fun": fun, "params": params}:
                        self.node = node_name(fun)
                        self.params = ParamContainer(params, ParamType.RULE_PARAM)
                        self.funcon = funcon
                        self.has_two_varargs = (
                            sum([param.value_type.value > 0 for param in self.params])
                            == 2
                        )
                    case value:
                        self.value = value
                self.rewrites_to = rewrites_to
            case {"premises": premises, "conclusion": conclusion}:
                self.premises = [Rule(premise, funcon) for premise in premises]
                self.conclusion = Rule(conclusion, funcon)

    def __str__(self) -> str:
        return str(self.data)


class Funcon:
    def __init__(self, data) -> None:
        self.data = data
        self.definition = self.data["definition"]
        self.name = self.definition["name"]

        self.params = ParamContainer(
            self.definition.get("params"),
            ParamType.FUNCON_PARAM,
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
        else:
            self.n_final_args = 0
            self.n_regular_args = self.n_params

        if "rewrites_to" in self.definition:
            self.rewrites_to = self.definition["rewrites_to"]
            self.rules = None
        else:
            self.rules = [Rule(rule, self) for rule in self.data.get("rules", [])]
            self.rewrites_to = None

        self.return_type = Type(self.definition["returns"])

        self.signature = (
            f"class {node_name(self.name)}({self.params}) : {FUNCON_NODE}()"
        )

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

    def rewrite_expr(self, expr, params: ParamContainer):
        def make_arg_str(value):
            n_term_params = len(params)
            param = params[str(value)]

            if param is None:
                return f'{self.return_type}("{value}")'

            if param.value_type.value > 0:
                if self.n_final_args == 0:
                    vararg_part = f"*Util.slice(p{self.vararg_index}, {param.idx})"
                    return vararg_part
                else:
                    vararg_part = f"*Util.slice(p{self.vararg_index}, {param.idx}, {self.n_final_args})"
                    final_part = ", ".join(
                        [
                            f"p{i}=p{self.vararg_index}[{i}]"
                            for i in range(
                                self.vararg_index + 1,
                                self.vararg_index + self.n_final_args + 1,
                            )
                        ]
                    )
                    return f"{vararg_part}, {final_part}"

            return self.make_param_str(param.idx, n_term_params)

        return recursive_call(expr, make_arg_str)

    def node_body(self):
        conditions = {
            param: f"{self.make_param_str(param.idx, len(self.params))} is {DATATYPE_NODE}"
            for param in self.params
            if not param.type.is_lazy
        }
        kt_rules = []
        for rule in self.rules:
            if not "term" in rule.data:
                continue
            rule_conditions = []

            if len(rule.params) == 0 or len(rule.params) == self.n_final_args:
                rule_param = f"p{self.vararg_index}"
                kt_condition = f"{rule_param}.isEmpty()"
            else:
                for param in rule.params:
                    rule_param = self.make_param_str(param.idx, len(rule.params))

                    condition = f'{rule_param}.execute(frame) == "{param.value}"'
                    rule_conditions.append(condition)

                kt_condition = " && ".join(rule_conditions)

            kt_returns = self.rewrite_expr(rule.rewrites_to, rule.params)
            if kt_returns is None:
                kt_returns = f'{self.return_type}("{rule.rewrites_to}")'
            kt_rule = f"{kt_condition} -> return {kt_returns}"
            kt_rules.append(kt_rule)
        kt_rules.append("else -> throw IllegalArgumentException()")

        when_body = "\n".join(kt_rules)
        rules_body = f"when {make_body(when_body)}"
        if len(conditions) > 0:
            conditionblock = (
                f"if ({' && '.join(conditions.values())}) {make_body(rules_body)}"
            )
        else:
            conditionblock = rules_body

        kt_steps = []
        for param in conditions.keys():
            param_str = self.make_param_str(param.idx, len(self.params))
            execute_body = f"val e{param.idx} = {param_str}.execute(frame)"
            node_params = ", ".join(
                [
                    param_str if param_str_idx != param.idx else f"e{param.idx}"
                    for param_str_idx, param_str in enumerate(
                        self.make_param_str(param.idx, len(self.params))
                        for param in self.params
                    )
                ]
            )
            execute_body += f"\nreturn {node_name(self.name)}({node_params})"
            kt_step = f"if ({param_str} is {FUNCON_NODE}) {make_body(execute_body)}"
            kt_steps.append(kt_step)
        body = conditionblock + "\n" + "\n".join(kt_steps)
        return body

    @property
    def rule_body(self):
        body = self.node_body()

        return f"@Override\noverride fun execute(frame: VirtualFrame): {CBS_NODE} {make_body(body)}"

    @property
    def rewrite_body(self):
        kt_return = f"return {self.rewrite_expr(self.rewrites_to, self.params)}"
        body = f"@Override\noverride fun execute(frame: VirtualFrame): {CBS_NODE} {make_body(kt_return)}"
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
        return f"{self.signature} {self.body}"


@dataclass
class FileData:
    def __init__(self):
        self.datatypes: dict[str, Datatype] = {}
        self.funcons: dict[str, Funcon] = {}


class CodeGen:
    filedata: dict[str, FileData] = {}

    def get(
        self, attribute: str = "datatypes" | "funcons"
    ) -> list[Datatype] | list[Funcon]:
        return [
            obj
            for file in self.filedata.values()
            for obj in getattr(file, attribute).values()
        ]

    def __init__(self, cbs_dir) -> None:
        pattern = os.path.join(cbs_dir, "**/*.cbs")
        cbs_files = glob.glob(pattern, recursive=True)

        for path in cbs_files:
            filename = Path(path).stem
            self.filedata[filename] = FileData()

            print(filename)
            skip = not any(x in path for x in ["Flowing", "Booleans", "Null"])
            ast = parse_cbs_file(path, dump_json=False).asDict()
            if "datatypes" in ast:
                for datatype_ast in ast["datatypes"]:
                    datatype = Datatype(datatype_ast)
                    datatype.skip = skip
                    self.filedata[filename].datatypes[datatype.name] = datatype
            if "funcons" in ast:
                for funcon_ast in ast["funcons"]:
                    funcon = Funcon(funcon_ast)
                    funcon.skip = skip
                    self.filedata[filename].funcons[funcon.name] = funcon

    def generate(self):
        truffle_api_imports = "\n".join(
            [
                "package com.trufflegen.generated",
                "import com.trufflegen.stc.*",
                "import com.trufflegen.generated.*",
            ]
            + [
                f"import com.oracle.truffle.api.{i}"
                for i in [
                    "frame.VirtualFrame",
                    "nodes.Node",
                    "nodes.Node.Child",
                    "dsl.Specialization",
                ]
            ]
        )

        print(self.get("datatypes") + self.get("funcons"))

        code = "\n\n".join(
            [
                obj.code
                for obj in self.get("datatypes") + self.get("funcons")
                if not obj.skip
            ]
        )

        code = truffle_api_imports + "\n\n" + code

        return code


def main():
    parser = argparse.ArgumentParser(description="Generate code from CBS file")
    parser.add_argument(
        "cbs_dir",
        help="Generate kotlin files for all .cbs files in specified direcotry",
    )
    parser.add_argument(
        "-w", "--write-kotlin", help="Write output to kotlin file", action="store_true"
    )
    args = parser.parse_args()
    generator = CodeGen(args.cbs_dir)
    generator.generate()


if __name__ == "__main__":
    main()
