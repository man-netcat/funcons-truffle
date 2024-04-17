from enum import Enum
from parser_builder import parse_cbs_file


def node_name(name):
    return "".join(w.capitalize() for w in name.split("-")) + "Node"


def make_body(str):
    return "{\n" + "\n".join(["    " + line for line in str.splitlines()]) + "\n}"


class Datatype:
    def __init__(self, data) -> None:
        self.name = data["name"]
        self.def_ = data["definition"]

    @property
    def signature(self):
        return f"class {node_name(self.name)} : Node"

    @property
    def body(self):
        return make_body(f"override fun execute(): {self.name} = {self.def_}")

    @property
    def code(self):
        return "\n".join([self.signature, self.body])


class Rule:
    def __init__(self, data) -> None:
        self.term = data["term"]
        self.fun_node = node_name(self.term["fun"])
        self.fun_params = self.term["params"]
        self.rewrites_to = data["rewrites_to"]


class ParamType(Enum):
    ARG = 1
    VARARG = 2


class Param:
    def __init__(self, data) -> None:
        self.data = data
        self.value = data["value"]
        self.type = data["type"]

    def __repr__(self) -> str:
        return str(self.data)

    def code(self, i):
        self.value_str = f"p{i}"

        match self.type:
            case [type, "*"]:
                self.param_str = f"vararg {self.value_str}: {node_name(type)}"
                self.param_type = ParamType.VARARG
            case type:
                self.param_str = f"{self.value_str}: {node_name(type)}"
                self.param_type = ParamType.ARG
        return self.param_str


class Funcon:
    def __init__(self, data) -> None:
        self.def_ = data["definition"]
        self.name = self.def_["name"]
        self.params = [Param(param) for param in self.def_["params"]]
        self.rules = [Rule(rule) for rule in data["rules"]]
        self.returns = self.def_["returns"]

    @property
    def param_str(self):
        return ", ".join([param.code(i) for i, param in enumerate(self.params)])

    @property
    def signature(self):
        return f"class {node_name(self.name)}({self.param_str}) : Node"

    def rw(self, rule: Rule):
        term_params = rule.term["params"]
        if len(self.params) == len(term_params):
            condition = " && ".join(
                [
                    f"{fun_param.value_str} == {term_param['value']}"
                    for fun_param, term_param in zip(self.params, term_params)
                ]
            )
        elif len(term_params) == 0:
            condition = "p0.isEmpty()"
        else:
            conditions = []
            for i, param in enumerate(term_params):
                match param["value"]:
                    case [_, "*"]:
                        pass
                    case value:
                        conditions.append(f"p[{i}] == {value}")
            condition = " && ".join(conditions)

        match rule.rewrites_to:
            case {"fun": rw_call, "params": rw_params}:
                rw_node = node_name(rw_call)
                returns = f"return {rw_node}({rw_params})"
            case literal:
                returns = f"return {literal}"

        return "\n".join([f"if ({condition})", make_body(returns)])

    @property
    def body(self):
        match self.returns:
            case ["=>", returns] | returns:
                self.return_str = node_name(returns)

        rewrite_results = "\n".join([self.rw(rule) for rule in self.rules])

        body = make_body(
            f"override fun execute(): {self.return_str}()\n{make_body(rewrite_results)}"
        )
        return body

    @property
    def code(self):
        return "\n".join([self.signature, self.body])


class CodeGen:
    def __init__(self, path) -> None:
        self.ast = parse_cbs_file(path).asDict()
        self.datatypes = self.ast["datatypes"]
        self.funcons = self.ast["funcons"]

    @property
    def generate(self):
        objs = ["import com.oracle.truffle.api.nodes.Node"]

        dtype_strs = []
        for datatype in self.datatypes:
            dtype_cls = Datatype(datatype)
            dtype_strs.append(dtype_cls.code)
        objs.extend(dtype_strs)

        funcon_strs = []
        for funcon in self.funcons:
            funcon_cls = Funcon(funcon)
            funcon_strs.append(funcon_cls.code)
        objs.extend(funcon_strs)

        return "\n\n".join(objs)


if __name__ == "__main__":
    path = "/home/rick/workspace/thesis/CBS-beta/Funcons-beta/Values/Primitive/Booleans/Booleans.cbs"
    # path = "/home/rick/workspace/thesis/CBS-beta/Funcons-beta/Computations/Normal/Flowing/Flowing.cbs"

    generator = CodeGen(path)
    code = generator.generate
    print(code)
