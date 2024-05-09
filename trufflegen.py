import argparse
from pprint import pprint

from parser_builder import parse_cbs_file
from icecream.icecream import ic


def node_name(name):
    if name == "T":
        return "ExprNode"
    return "".join(w.capitalize() for w in str(name).split("-")) + "Node"


def make_body(str):
    return "{\n" + "\n".join(["    " + line for line in str.splitlines()]) + "\n}"


def class_signature(name, params):
    return f"class {node_name(name)}({params}) : Node()"


def make_param_objs(param_data):
    return [Param(param, i) for i, param in enumerate(param_data)]


def extract_computes(str):
    match str:
        case ["=>", r] | r:
            return r


def make_arg_str(arg):
    match arg:
        case [*args]:
            return "".join([extract_computes(arg) for arg in args])
        case arg:
            return extract_computes(arg)


def is_vararg(value):
    match value:
        case [value, "*" | "+"]:
            return True
        case value:
            return False


def generate_function_call(data):
    match data:
        case {"fun": fun, "params": params}:
            param_strings = [generate_function_call(param) for param in params]
            return f"{node_name(fun)}({', '.join(param_strings)})"
        case {"value": value}:
            return generate_function_call(value)
        case literal:
            return f'"{str(literal)}"'


class Datatype:
    def __init__(self, data) -> None:
        self.name = data["name"]
        self.def_ = data["definition"]

    @property
    def code(self):
        return "\n".join(
            [
                class_signature(self.name, "private val value: String"),
                make_body(f"fun execute(frame: VirtualFrame): String = value"),
            ]
        )


class Rule:
    def __init__(self, data) -> None:
        self.data = data
        if "term" in data:
            self.term = data["term"]
            self.node = node_name(self.term["fun"])
            self.params = (
                make_param_objs(self.term["params"]) if "params" in self.term else []
            )
            self.param_map = dict(
                zip(
                    [(make_arg_str(param.value)) for param in self.params],
                    self.params,
                )
            )
            self.rewrites_to = data["rewrites_to"]
        elif "premises" in data:
            self.premises = data["premises"]
            self.conclusion = data["conclusion"]

    def __str__(self) -> str:
        return str(self.data)


class Param:
    def __init__(self, data, i) -> None:
        self.data = data
        self.value = data["value"]
        self.type = data["type"] if "type" in data else None

        match extract_computes(self.type):
            case [type, "*" | "+"]:
                self.param_type = type
                self.vararg = True
            case type:
                self.param_type = type
                self.vararg = False

        # Type can be an array-like
        match extract_computes(self.param_type):
            case [t, "*" | "+"]:  # Array type
                self.kt_type = f"Array<{node_name(t)}>"
                self.array_type = True
            case t:  # Single type
                self.kt_type = f"{node_name(t)}"
                self.array_type = False

        self.param_idx = i

    def __repr__(self) -> str:
        return str(self.data)

    @property
    def make_param_str(self):
        return f"@Child private {'vararg ' if self.vararg else ''}val p{self.param_idx}: {self.kt_type}"


class Funcon:
    def __init__(self, data) -> None:
        self.data = data
        self.definition = self.data["definition"]
        self.name = self.definition["name"]

        self.params = (
            make_param_objs(self.definition["params"])
            if "params" in self.definition
            else []
        )

        if sum([param.vararg for param in self.params]) > 1:
            raise ValueError("Somehow more than 1 vararg???")

        self.has_varargs = any([param.vararg for param in self.params])
        self.vararg_index = next(
            (i for i, param in enumerate(self.params) if param.vararg), -1
        )
        self.n_final_args = (
            len(self.params) - self.vararg_index - 1 if self.has_varargs else 0
        )
        self.n_regular_args = (
            len(self.params) - self.n_final_args - (1 if self.has_varargs else 0)
        )

        if "rewrites_to" in self.definition:
            self.rewrites_to = self.definition["rewrites_to"]
            self.rules = None
        else:
            self.rules = (
                [Rule(rule) for rule in self.data["rules"]]
                if "rules" in self.data
                else []
            )
            self.rewrites_to = None
        self.returns = self.definition["returns"]
        self.return_str = extract_computes(self.returns)

    @property
    def param_str(self):
        return ", ".join([param.make_param_str for param in self.params])

    @property
    def signature(self):
        return class_signature(self.name, self.param_str)

    def make_condition(self, kt_param, param: Param):
        if param is None:
            return f"{kt_param}.isEmpty()"
        elif is_vararg(param.value):
            return "Unimplemented Condition"
        else:
            return f'{kt_param}.execute(frame) == "{make_arg_str(param.value)}"'

    def make_kt_param(self, index, n_params, is_vararg):
        num_varargs = n_params - (self.n_regular_args + self.n_final_args)

        if n_params < self.n_regular_args + self.n_final_args or index >= n_params:
            return "error"
        elif index < self.n_regular_args:
            return f"p{index}"
        elif index < self.n_regular_args + num_varargs:
            if is_vararg:
                return f"*p{self.n_regular_args}.sliceArray({index - self.n_regular_args}..p{self.n_regular_args}.size)"
            else:
                return f"p{self.n_regular_args}[{index - self.n_regular_args}]"
        else:
            return f"p{index - num_varargs + 1}"

    def make_rewrite_node(self, expr):
        match expr:
            case {"fun": fun, "params": params}:
                param_str = ", ".join(
                    [self.make_rewrite_node(param["value"]) for param in params]
                )
                return f"{node_name(fun)}({param_str})"
            case value:
                for i, param in enumerate(self.params):
                    if param.value == value:
                        return self.make_kt_param(i, len(self.params), is_vararg(value))
                return f'{node_name(self.return_str)}("{value}")'

    def make_rule_param(self, arg, rule: Rule):
        arg_str = make_arg_str(arg)
        match arg_str:
            case {"fun": fun, "params": params}:
                param_str = ", ".join(
                    [self.make_rule_param(param["value"], rule) for param in params]
                )
                return f"{node_name(fun)}({param_str})"
            case value:
                n_term_params = len(rule.params)
                param = rule.param_map.get(value)

                if not param:
                    return None

                param_index = rule.params.index(param)
                return self.make_kt_param(
                    param_index, n_term_params, is_vararg(param.value)
                )

    def build_term_rewrite(self, rule: Rule):
        conditions = []

        if len(rule.params) == 0:
            rule_param = f"p{self.vararg_index}"
            kt_condition = self.make_condition(rule_param, None)
        else:
            for param in rule.params:
                value = make_arg_str(param.value)
                rule_param = self.make_rule_param(value, rule)
                condition = self.make_condition(rule_param, param)
                if condition:
                    conditions.append(condition)

            kt_condition = " && ".join(conditions)

        kt_returns = self.make_rule_param(rule.rewrites_to, rule)
        if kt_returns is None:
            kt_returns = f'{node_name(self.return_str)}("{rule.rewrites_to}")'
        return f"{kt_condition} -> {kt_returns}"

    def build_step_rewrite(self, rule: Rule):
        for premise in rule.premises:
            pass
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
        return "\n".join([self.signature, self.body])


class CodeGen:
    def __init__(self, path) -> None:
        self.ast = parse_cbs_file(path).asDict()
        self.datatypes = self.ast["datatypes"] if "datatypes" in self.ast else ""
        self.funcons = self.ast["funcons"]

    @property
    def generate(self):
        truffle_api_imports = "\n".join(
            [
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

        return truffle_api_imports + "\n\n" + allclasses


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate code from CBS file")
    parser.add_argument("cbs_file", help="Path to the CBS file")
    args = parser.parse_args()

    generator = CodeGen(args.cbs_file)
    code = generator.generate
    print(code)
