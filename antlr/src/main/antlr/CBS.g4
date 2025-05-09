grammar CBS;

@ header
{
package cbs;
}
root
   : (index | object)* EOF
   ;

definitionId
   : DATATYPE
   | FUNCON
   | TYPE
   | ENTITY
   ;

indexLine
   : (definitionId name = IDENTIFIER (ALIAS alias = IDENTIFIER)?)
   ;

index
   : '[' indexLine+ ']'
   ;

metavarDef
   : variables = exprs SUBTYPE supertype = expr
   ;

object
   : (modifier = (BUILTIN | AUXILIARY)? FUNCON name = IDENTIFIER ('(' params ')')? COLON returnType = expr (REWRITE rewritesTo = expr)? (ALIAS aliasDefinition | RULE ruleDefinition | ASSERT assertDefinition)*) # FunconDefinition
   | (modifier = BUILTIN? DATATYPE name = IDENTIFIER ('(' params ')')? op = ('::=' | SUBTYPE) definition = expr (ALIAS aliasDefinition | ASSERT assertDefinition)*) # DatatypeDefinition
   | (modifier = BUILTIN? TYPE name = IDENTIFIER ('(' params ')')? (op = (REWRITE | SUBTYPE) definition = expr)? (ALIAS aliasDefinition)*) # TypeDefinition
   | METAVARIABLES metavarDef+ # MetavariablesDefinition
   | ENTITY VARIABLE '--' (name = IDENTIFIER polarity = ('!' | '?')? ('(' params ')')?) '->' VARIABLE (ALIAS aliasDefinition)* # ControlEntityDefinition
   | ENTITY def = '<' VARIABLE ',' (name = IDENTIFIER ('(' lhs = params) ')')? '>' TRANSITION '<' VARIABLE ',' (IDENTIFIER ('(' rhs = params ')')?) '>' (ALIAS aliasDefinition)* # MutableEntityDefinition
   | ENTITY name = IDENTIFIER ('(' params ')')? HASCONTEXT VARIABLE TRANSITION VARIABLE (ALIAS aliasDefinition)* # ContextualEntityDefinition
   ;

assertDefinition
   : expr '==' expr
   ;

aliasDefinition
   : name = IDENTIFIER '=' original = IDENTIFIER
   ;

squote
   : SQUOTE
   ;

expr
   : name = IDENTIFIER args # FunconExpression
   | operand = expr POW power = expr # PowerExpression
   | operand = expr op = (STAR | PLUS | QMARK) # SuffixExpression
   | op = COMPLEMENT operand = expr # ComplementExpression
   | op = COMPUTES operand = expr # UnaryComputesExpression
   | lhs = expr op = COMPUTES rhs = expr # BinaryComputesExpression
   | lhs = expr op = (EQUALS | NOTEQUALS) rhs = expr # EqualityExpression
   | lhs = expr op = AND rhs = expr # AndExpression
   | lhs = expr op = OR rhs = expr # OrExpression
   | value = expr op = COLON type = expr # TypeExpression
   | '(' expr ')' # NestedExpression
   | '[' elements = exprs? ']' # ListExpression
   | '{' elements = exprs? '}' # SetExpression
   | '{' pairs '}' # MapExpression
   | '(' elements = exprs? ')' # SequenceExpression
   | string = STRING # String
   | value = NUMBER # Number
   | varname = VARIABLE squote* # Variable
   ;

exprs
   : expr (',' expr)*
   ;

args
   : '(' exprs? ')' # MultipleArgs
   | expr # SingleArgs
   | # NoArgs
   ;

param
   : (value = expr COLON)? type = expr
   ;

params
   : param (',' param)*
   ;

label
   : name = IDENTIFIER polarity = ('!' | '?')? '(' value = expr? ')'
   ;

labels
   : label (',' label)*
   ;

step
   : '--' labels '->' sequenceNumber = NUMBER?
   ;

steps
   : step (';' step)*
   ;

premiseExpr
   : context_ = label HASCONTEXT lhs = expr TRANSITION rhs = expr # TransitionPremiseWithContextualEntity
   | '<' lhs = expr ',' entityLhs = label '>' TRANSITION '<' rhs = expr ',' entityRhs = label '>' # TransitionPremiseWithMutableEntity
   | lhs = expr steps rhs = expr # TransitionPremiseWithControlEntity
   | lhs = expr TRANSITION rhs = expr # TransitionPremise
   | lhs = expr REWRITE rhs = expr # RewritePremise
   | lhs = expr op = (EQUALS | NOTEQUALS) rhs = expr # BooleanPremise
   | value = expr COLON type = expr # TypePremise
   ;

premises
   : premiseExpr+
   ;

ruleDefinition
   : (premises BAR)? conclusion = premiseExpr
   ;

pair
   : key = expr '|->' value = expr
   ;

pairs
   : (pair (',' pair)*)?
   ;

STAR
   : '*'
   ;

PLUS
   : '+'
   ;

QMARK
   : '?'
   ;

POW
   : '^'
   ;

COMPUTES
   : '=>'
   ;

COMPLEMENT
   : '~'
   ;

AND
   : '&'
   ;

OR
   : '|'
   ;

COLON
   : ':'
   ;

NOTEQUALS
   : '=/='
   ;

EQUALS
   : '=='
   ;

HASCONTEXT
    : '|-'
    ;

WS
   : [ \t\r\n]+ -> skip
   ;

COMMENT
   : '#' ('#' | ~ [\r\n])* -> skip
   ;

BLOCKCOMMENT
   : '/*' .*? '*/' -> skip
   ;

STRING
   : '"' .*? '"'
   ;

CHARACTER
   : '\'' . '\''
   ;

NUMBER
   : '-'? [0-9]+ ('.' [0-9]+)?
   ;

DATATYPE
   : 'Datatype'
   ;

FUNCON
   : 'Funcon'
   ;

ALIAS
   : 'Alias'
   ;

RULE
   : 'Rule'
   ;

ENTITY
   : 'Entity'
   ;

TYPE
   : 'Type'
   ;

METAVARIABLES
   : 'Meta-variables'
   ;

BUILTIN
   : 'Built-in'
   ;

AUXILIARY
   : 'Auxiliary'
   ;

ASSERT
   : 'Assert'
   ;

BAR
   : '----' '-'*
   ;

REWRITE
   : '~>'
   ;

TRANSITION
   : '--->'
   ;

SUBTYPE
   : '<:'
   ;

SQUOTE
   : '\''
   ;

VARIABLE
   : [A-Z] [a-zA-Z0-9-]*
   | '_'
   ;

IDENTIFIER
   : [A-Za-z] [a-zA-Z0-9-]*
   ;

