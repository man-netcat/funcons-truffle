import argparse
from pprint import pprint

from parser_builder import parse_cbs_file


def node_name(name):
    return "".join(w.capitalize() for w in str(name).split("-")) + "Node"


def make_body(str):
    return "{\n" + "\n".join(["    " + line for line in str.splitlines()]) + "\n}"


def class_signature(name, params):
    return f"class {node_name(name)}({params}) : Node()"


def make_param_objs(param_data):
    return [Param(param, i) for i, param in enumerate(param_data)]


def make_slice(array, start_idx):
    return f"*{array}.sliceArray({start_idx}..{array}.size)"


def type_str(type):
    match type:
        # Extract computes
        case ["=>", inner_type] | inner_type:
            match inner_type:
                case [t, "*"] | [t, "+"]:  # Array type
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
            self.term_params = make_param_objs(self.term["params"])
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
        self.param_idx = i
        self.param_value_str = f"p{i}"
        self.value_str = f"this.p{i}"

    def __repr__(self) -> str:
        return str(self.data)

    def make_param_str(self, param_type, vararg):
        return f"@Child private {'vararg ' if vararg else ''}val {self.param_value_str}: {type_str(param_type)}"

    @property
    def code(self):
        match self.type:
            case [type, "*"]:
                return self.make_param_str(type, True)
            case type:
                return self.make_param_str(type, False)


class Funcon:
    def __init__(self, data) -> None:
        self.def_ = data["definition"]
        self.name = self.def_["name"]
        self.params = make_param_objs(self.def_["params"])
        self.rules = [Rule(rule) for rule in data["rules"]]
        self.returns = self.def_["returns"]

    @property
    def vararg(self):
        return self.params[0].value_str

    @property
    def param_str(self):
        return ", ".join([param.code for param in self.params])

    @property
    def signature(self):
        return class_signature(self.name, self.param_str)

    def make_condition(self, param, condition):
        return f'{param}.execute(frame) == "{condition}"'

    @property
    def body(self):
        match self.returns:
            case ["=>", returns] | returns:
                self.return_str = returns

        lines = []
        for rule in self.rules:
            term_params = rule.term_params

            if len(term_params) == len(self.params):
                condition = " && ".join(
                    [
                        self.make_condition(fun_param.value_str, term_param.value)
                        for fun_param, term_param in zip(self.params, term_params)
                    ]
                )
            elif len(term_params) == 0:
                condition = f"{self.vararg}.isEmpty()"
            elif len(term_params) > len(self.params):
                conditions = []
                for i, param in enumerate(term_params):
                    match param.value:
                        case [_, "*"]:
                            varargs_idx = i
                            break
                        case [_, "+"]:
                            conditions.append(
                                self.make_condition(f"{self.vararg}[{i}]", value)
                            )
                            varargs_idx = i + 1
                            break
                        case value:
                            conditions.append(
                                self.make_condition(f"{self.vararg}[{i}]", value)
                            )
                condition = " && ".join(conditions)

            match rule.rewrites_to:
                case {"fun": rw_call, "params": rw_params}:
                    rw_node = node_name(rw_call)
                    return_node_args = make_slice(self.vararg, varargs_idx)
                    returns = f"{rw_node}({return_node_args})"
                case literal:
                    returns = f'{node_name(self.return_str)}("{literal}")'
            lines.append(f"{condition} -> {returns}")
        lines.append("else -> throw IllegalArgumentException()")
        fun_body = make_body("\n".join(lines))
        body = f"@Override\nfun execute(frame: VirtualFrame): Any = when {fun_body}"

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
