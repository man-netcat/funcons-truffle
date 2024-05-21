import argparse
import glob
import os
from enum import Enum, Flag
from pathlib import Path

from icecream.icecream import ic
from parser_builder import parse_cbs_file

ic.configureOutput(includeContext=True)


def node_name(name):
    return "".join(w.capitalize() for w in str(name).split("-"))


def make_body(str):
    return "{\n" + "\n".join(["    " + line for line in str.splitlines()]) + "\n}"


def class_signature(name, params):
    return f"class {node_name(name)}({params}) : Node()"


def recursive_call(expr, value_fun):
    match expr:
        case {"fun": fun, "params": params}:
            param_str = ", ".join(
                [recursive_call(param["value"], value_fun) for param in params]
            )
            return f"{node_name(fun)}({param_str})"
        case value:
            return value_fun(value)


class VarType(Flag):
    NORMAL = 0
    STAR = 1
    PLUS = 2
    OPT = 3


class TypeAttribute(Enum):
    VARARG = 0
    LAZY = 1


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


class Datatype:
    def __init__(self, data) -> None:
        self.name = data["name"]
        self.def_ = data["definition"]

    @property
    def code(self):
        return " ".join(
            [
                class_signature(self.name, "private val value: String"),
                make_body(f"fun execute(frame: VirtualFrame): String = value"),
            ]
        )


class Rule:
    def __init__(self, data) -> None:
        self.data = data

        match data:
            case {"term": term, "rewrites_to": rewrites_to}:
                self.term = term
                match self.term:
                    case {"fun": fun, "params": params}:
                        self.node = node_name(fun)
                        self.params = Params(params)
                        self.has_two_varargs = (
                            sum([param.value_type.value > 0 for param in self.params])
                            == 2
                        )
                    case value:
                        self.value = value
                self.rewrites_to = rewrites_to
            case {"premises": premises, "conclusion": conclusion}:
                self.premises = [Rule(premise) for premise in premises]
                self.conclusion = Rule(conclusion)

    def __str__(self) -> str:
        return str(self.data)


class Params:
    def __init__(self, params) -> None:
        if params is None:
            self.params = []
        else:
            self.params = [Param(param, i) for i, param in enumerate(params)]

    def __contains__(self, value):
        return value in (param.value for param in self.params)

    def __len__(self):
        return len(self.params)

    def __iter__(self):
        self.index = 0
        return self

    def __next__(self):
        if self.index < len(self.params):
            param = self.params[self.index]
            self.index += 1
            return param
        else:
            raise StopIteration


class Type:
    def __init__(self, data):
        if data is None:
            self.type = None
            return

        self.type, type_attributes = self.get_type_attributes(data)

        vararg_count = type_attributes.count(TypeAttribute.VARARG)
        lazy_count = type_attributes.count(TypeAttribute.LAZY)

        if vararg_count > 2 or lazy_count > 1:
            raise ValueError(f"Unimplemented type attributes {type_attributes}")

        self.is_vararg = vararg_count > 0
        self.is_array = vararg_count == 2
        self.is_lazy = lazy_count == 1

        if self.is_array:
            self.str = f"Array<{node_name(self.type)}>"
        else:
            self.str = f"{node_name(self.type)}"

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


class Param:
    def __init__(self, data, i) -> None:
        self.data = data
        self.value = data["value"]
        self.type = Type(data.get("type"))
        self.param_idx = i
        self.value_type = value_type(self.value)

    def __repr__(self) -> str:
        return str(self.data)

    @property
    def make_param_str(self):
        return f"@Child private {'vararg ' if self.type.is_vararg else ''}val p{self.param_idx}: {self.type.str}"


class Funcon:
    def __init__(self, data) -> None:
        self.data = data
        self.definition = self.data["definition"]
        self.name = self.definition["name"]

        print(self.name)

        self.params = Params(self.definition.get("params"))
        self.n_params = len(self.params)

        if sum([param.type.is_vararg for param in self.params]) > 1:
            raise ValueError("Somehow more than 1 vararg???")

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
            self.rules = [Rule(rule) for rule in self.data.get("rules", [])]
            self.rewrites_to = None
        self.return_type = Type(self.definition["returns"])
        self.param_str = ", ".join([param.make_param_str for param in self.params])
        self.signature = class_signature(self.name, self.param_str)

    def make_condition(self, kt_param, param: Param):
        if param is None:
            return f"{kt_param}.isEmpty()"
        else:
            return f'{kt_param}.execute(frame) == "{param.value}"'

    def make_kt_param(self, index, n_params, is_vararg):
        num_varargs = n_params - (self.n_regular_args + self.n_final_args)

        if n_params < self.n_regular_args + self.n_final_args:
            return "error"
        elif index < self.n_regular_args:
            return f"p{index}"
        elif index < self.n_regular_args + num_varargs:
            if is_vararg:
                if index - self.n_regular_args == 0:
                    return f"p{self.n_regular_args}"
                return f"*Util.slice(p{self.n_regular_args}, {index - self.n_regular_args})"
            return f"p{self.n_regular_args}[{index - self.n_regular_args}]"
        return f"p{index - num_varargs + 1}"

    def make_rewrite_node(self, expr):
        def helper(value):
            for i, param in enumerate(self.params):
                if param.value == value:
                    kt_param = self.make_kt_param(i, len(self.params), param.value_type)
                    return kt_param
            return f'{node_name(self.return_type.str)}("{value}")'

        return recursive_call(expr, helper)

    def make_rule_param(self, arg, rule: Rule):
        def helper(value):
            n_term_params = len(rule.params)
            try:
                param_index = [param.value for param in rule.params].index(value)
                is_vararg = value_type(value)
                kt_param = self.make_kt_param(param_index, n_term_params, is_vararg)
                return kt_param
            except ValueError:
                return f'"{value}"'

        return recursive_call(arg, helper)

    def build_term_rewrite(self, rule: Rule):
        conditions = []

        if len(rule.params) == 0:
            rule_param = f"p{self.vararg_index}"
            kt_condition = self.make_condition(rule_param, None)
        else:
            for param in rule.params:
                rule_param = self.make_rule_param(param.value, rule)
                condition = self.make_condition(rule_param, param)
                if condition:
                    conditions.append(condition)

            kt_condition = " && ".join(conditions)

        kt_returns = self.make_rule_param(rule.rewrites_to, rule)
        if kt_returns is None:
            kt_returns = f'{node_name(self.return_type.str)}("{rule.rewrites_to}")'
        return f"{kt_condition} -> {kt_returns}"

    def build_step_rewrite(self, rule: Rule):
        return "Unimplemented Step"

    @property
    def rule_body(self):
        lines = []
        for rule in self.rules:
            if "term" in rule.data:
                line = self.build_term_rewrite(rule)
            else:
                line = self.build_step_rewrite(rule)
            lines.append(line)
        lines.append("else -> throw IllegalArgumentException()")
        fun_body = make_body("\n".join(lines))
        body = f"@Override\nfun execute(frame: VirtualFrame): Any = when {fun_body}"

        return body

    @property
    def rewrite_body(self):
        kt_return = f"return {self.make_rewrite_node(self.rewrites_to)}"
        fun_body = make_body(kt_return)
        body = f"@Override\nfun execute(frame: VirtualFrame): Any {fun_body}"
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


class CodeGen:
    def __init__(self, path) -> None:
        self.ast = parse_cbs_file(path).asDict()
        self.datatypes = self.ast["datatypes"] if "datatypes" in self.ast else []
        self.funcons = self.ast["funcons"] if "funcons" in self.ast else []

    def generate(self):
        truffle_api_imports = "\n".join(
            [
                "package com.trufflegen.generated",
                "import com.trufflegen.stc.*",
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

        classes = []

        dtype_strs = []
        for datatype in self.datatypes:
            dtype_cls = Datatype(datatype)
            dtype_strs.append(dtype_cls.code)
        classes.extend(dtype_strs)

        funcon_strs = []
        for funcon in self.funcons:
            funcon_cls = Funcon(funcon)
            funcon_strs.append(funcon_cls.code)
        classes.extend(funcon_strs)

        allclasses = "\n\n".join(classes)

        code = truffle_api_imports + "\n\n" + allclasses

        return code


def main():
    def generate(path, write=False):
        print(path)
        generator = CodeGen(path)
        code = generator.generate()
        if write:
            filename = Path(path).stem
            kt_path = (
                f"kt_source/src/main/kotlin/com/trufflegen/generated/{filename}.kt"
            )
            with open(kt_path, "w") as f:
                f.write(code)
        else:
            print(code)

    parser = argparse.ArgumentParser(description="Generate code from CBS file")
    parser.add_argument(
        "-d",
        "--directory",
        help="Generate kotlin files for all .cbs files in specified direcotry",
    )
    parser.add_argument(
        "-f",
        "--file",
        help="Generate kotlin file for given .cbs file",
    )
    parser.add_argument(
        "-w", "--write-kotlin", help="Write output to kotlin file", action="store_true"
    )
    args = parser.parse_args()
    if args.directory:
        pattern = os.path.join(args.directory, "**/*.cbs")
        cbs_files = glob.glob(pattern, recursive=True)
        for path in cbs_files:
            if not any(x in path for x in ["Flowing", "Booleans"]):
                continue
            generate(path, args.write_kotlin)
    elif args.file:
        generate(args.file, args.write_kotlin)
    else:
        raise ValueError("Specify either -d/--directory or -f/--file.")


if __name__ == "__main__":
    main()
