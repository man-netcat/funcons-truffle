import argparse
from pprint import pprint

from parser_builder import parse_cbs_file
from icecream.icecream import ic


def node_name(name):
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
            return "".join(args)
        case arg:
            return arg


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


def type_str(type):
    match type:
        # Extract computes
        case ["=>", inner_type] | inner_type:
            match inner_type:
                case [t, "*" | "+"]:  # Array type
                    param_type_str = f"Array<{node_name(t)}>"
                case t:  # Single type
                    param_type_str = f"{node_name(t)}"
    return param_type_str


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
            self.term_node = node_name(self.term["fun"])
            self.term_params: list[dict] = self.term["params"]
            self.param_map = dict(
                zip(
                    [(make_arg_str(param["value"])) for param in self.term_params],
                    self.term_params,
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
        match self.type:
            case [type, "*" | "+"]:
                self.param_type = type
                self.vararg = True
            case type:
                self.param_type = type
                self.vararg = False
        self.kt_type = type_str(self.param_type)
        self.param_idx = i
        self.kt_param = f"p{i}"
        self.kt_param_exec = f"this.p{i}"

    def __repr__(self) -> str:
        return str(self.data)

    @property
    def make_param_str(self):
        return f"@Child private {'vararg ' if self.vararg else ''}val {self.kt_param}: {self.kt_type}"


class ParamIndexer:
    def __init__(self, vararg_index=-1, n_final_args=0) -> None:
        self.vararg_index = vararg_index
        self.n_final_args = n_final_args
        self.index = 0

    def __iter__(self):
        self.index = 0
        return self

    def __next__(self):
        try:
            arg = self[self.index]
            self.index += 1
            return arg
        except IndexError:
            raise StopIteration

    def __getitem__(self, index):
        if isinstance(index, slice):
            if index.start is None:
                return f"p{self.vararg_index}"
            elif index.stop is None:
                return f"p{index.start}"

            return f"*p{index.start}.sliceArray({index.stop}..p{index.start}.size)"

        if -index > self.n_final_args:
            raise IndexError("Invalid final argument")

        if self.vararg_index < 0 or (index >= 0 and index < self.vararg_index):
            return f"p{index}"
        elif index >= self.vararg_index:
            return f"p{self.vararg_index}[{index - self.vararg_index}]"
        elif index < 0:
            return f"p{self.vararg_index + self.n_final_args + index + 1}"


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
            len(self.params) - self.vararg_index if self.vararg_index != -1 else 0
        )
        self.param_indexer = ParamIndexer(self.vararg_index, self.n_final_args)

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

    def make_condition(self, param, condition):
        return f'{param}.execute(frame) == "{condition}"'

    def make_rule_node(self, arg, argindex, rule: Rule):
        if arg is None:
            return self.param_indexer[:]

        vararg = is_vararg(arg)
        arg_str = make_arg_str(arg)
        match arg_str:
            case {"fun": fun, "params": params}:
                param_str = ", ".join(
                    [
                        self.make_rule_node(
                            param["value"],
                            argindex,
                            rule,
                        )
                        for argindex, param in enumerate(params)
                    ]
                )
                return f"{node_name(fun)}({param_str})"
            case value:
                param = rule.param_map.get(value)

                if not param:
                    return None

                rewriteindex = rule.term_params.index(param)

                if rewriteindex == 0:
                    return self.param_indexer[argindex:]
                elif vararg:
                    return self.param_indexer[argindex:rewriteindex]
                else:
                    return self.param_indexer[rewriteindex]

    @property
    def rule_body(self):
        lines = []
        for rule in self.rules:
            try:
                term_params = rule.term_params
            except:
                continue
            if len(term_params) == 0:
                kt_param = self.make_rule_node(None, 0, rule)
                kt_condition = f"{kt_param}.isEmpty()"
            else:
                kt_condition = " && ".join(
                    [
                        self.make_condition(
                            self.make_rule_node(
                                term_param["value"],
                                term_index,
                                rule,
                            ),
                            term_param["value"],
                        )
                        for term_index, term_param in enumerate(term_params)
                        if not is_vararg(term_param["value"])
                    ]
                )
            kt_returns = self.make_rule_node(rule.rewrites_to, 0, rule)
            if kt_returns is None:
                kt_returns = f'{node_name(self.return_str)}("{rule.rewrites_to}")'
            lines.append(f"{kt_condition} -> {kt_returns}")
        lines.append("else -> throw IllegalArgumentException()")
        fun_body = make_body("\n".join(lines))
        body = f"@Override\nfun execute(frame: VirtualFrame): Any = when {fun_body}"

        return body

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
                        return self.param_indexer[i]
                return f'{node_name(self.return_str)}("{value}")'

    @property
    def rewrite_body(self):
        ic(self.definition)
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
