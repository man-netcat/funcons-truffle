from enum import Enum
from parser_builder import parse_cbs_file


def node_name(name):
    return "".join(w.capitalize() for w in name.split("-")) + "Node"


def make_body(str):
    return "{\n" + "\n".join(["    " + line for line in str.splitlines()]) + "\n}"


def class_signature(name, params):
    return f"class {node_name(name)}({params}) : Node()"


def make_param_objs(param_data):
    return [Param(param, i) for i, param in enumerate(param_data)]


def make_slice(array, start_idx):
    return f"*{array}.sliceArray({start_idx}..{array}.size)"


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
        self.term = data["term"]
        self.term_node = node_name(self.term["fun"])
        self.term_params = make_param_objs(self.term["params"])
        self.rewrites_to = data["rewrites_to"]


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

    def code(self, i):

        match self.type:
            case [type, "*"]:
                self.param_str = f"@Child private vararg val {self.param_value_str}: {node_name(type)}"
            case type:
                self.param_str = (
                    f"@Child private val {self.param_value_str}: {node_name(type)}"
                )
        return self.param_str


class Funcon:
    def __init__(self, data) -> None:
        self.def_ = data["definition"]
        self.name = self.def_["name"]
        self.params = make_param_objs(self.def_["params"])
        self.rules = [Rule(rule) for rule in data["rules"]]
        self.returns = self.def_["returns"]

    @property
    def param_str(self):
        return ", ".join([param.code(i) for i, param in enumerate(self.params)])

    @property
    def signature(self):
        return class_signature(self.name, self.param_str)

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
                        f'{fun_param.value_str}.execute(frame) == "{term_param.value}"'
                        for fun_param, term_param in zip(self.params, term_params)
                    ]
                )
            elif len(term_params) == 0:
                condition = f"{self.params[0].value_str}.isEmpty()"
            elif len(term_params) > len(self.params):
                conditions = []
                for i, param in enumerate(term_params):
                    match param.value:
                        case [_, "*"]:
                            varargs_idx = i
                            break
                        case value:
                            conditions.append(
                                f'{self.params[0].value_str}[{i}].execute(frame) == "{value}"'
                            )
                condition = " && ".join(conditions)

            match rule.rewrites_to:
                case {"fun": rw_call, "params": rw_params}:
                    rw_node = node_name(rw_call)
                    return_node_args = make_slice(self.params[0].value_str, varargs_idx)
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
        truffle_api_imports = (
            "\n".join(
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
            + "\n\n"
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

        return truffle_api_imports + "\n\n".join(classes)


if __name__ == "__main__":
    # path = "/home/rick/workspace/thesis/CBS-beta/Funcons-beta/Values/Primitive/Integers/Integers.cbs"
    path = "/home/rick/workspace/thesis/CBS-beta/Funcons-beta/Values/Primitive/Booleans/Booleans.cbs"
    # path = "/home/rick/workspace/thesis/CBS-beta/Funcons-beta/Computations/Normal/Flowing/Flowing.cbs"

    generator = CodeGen(path)
    code = generator.generate
    print(code)
