grammar CBS;

@ header
{
package trufflegen.antlr;
}
root
   : (index | obj)* EOF
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

obj
   : (modifier = (BUILTIN | AUXILIARY)? FUNCON name = IDENTIFIER ('(' params ')')? COLON returnType = expr (REWRITE rewritesTo = expr)? (ALIAS aliasDefinition | RULE ruleDefinition | ASSERT assertDefinition)*) # FunconDefinition
   | (modifier = BUILTIN? DATATYPE name = IDENTIFIER ('(' params ')')? op = ('::=' | SUBTYPE) definition = expr (ALIAS aliasDefinition | ASSERT assertDefinition)*) # DatatypeDefinition
   | (modifier = BUILTIN? TYPE name = IDENTIFIER ('(' params ')')? (op = (REWRITE | SUBTYPE) definition = expr)? (ALIAS aliasDefinition)*) # TypeDefinition
   | METAVARIABLES (variables = exprs SUBTYPE definition = expr)+ # MetavariablesDefinition
   | ENTITY (stepExpr | mutableExpr) (ALIAS aliasDefinition)* # EntityDefinition
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
   | operand = expr op = (STAR | PLUS | QMARK | POWN) # SuffixExpression
   | op = COMPLEMENT operand = expr # ComplementExpression
   | op = COMPUTES operand = expr # UnaryComputesExpression
   | lhs = expr op = COMPUTES rhs = expr # BinaryComputesExpression
   | lhs = expr op = (EQUALS | NOTEQUALS) rhs = expr # EqualityExpression
   | lhs = expr op = AND rhs = expr # AndExpression
   | lhs = expr op = OR rhs = expr # OrExpression
   | value = expr op = COLON type = expr # TypeExpression
   | value = expr op = SUBTYPE type = expr # SubTypeExpression
   | '(' expr ')' # NestedExpression
   | '[' exprs? ']' # ListExpression
   | '{' exprs? '}' # SetExpression
   | '{' pairs '}' # MapExpression
   | '(' exprs? ')' # TupleExpression
   | string = STRING # String
   | value = NUMBER # Number
   | varname = VARIABLE squote+ # VariableStep
   | varname = VARIABLE # Variable
   ;

exprs
   : expr (',' expr)*
   ;

args
   : '(' exprs ')' # MultipleArgs
   | '[' indices = exprs ']' # ListIndexExpression
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
   : '--->' sequenceNumber = NUMBER? # StepWithoutLabels
   | '--' labels '->' sequenceNumber = NUMBER? # StepWithLabels
   ;

steps
   : step (';' step)*
   ;

mutableExpr
   : '<' lhs = expr ',' entityLhs = label '>' '--->' '<' rhs = expr ',' entityRhs = label '>'
   ;

stepExpr
   : (context = expr '|-')? lhs = expr step rhs = expr # StepExprWithSingleStep
   | (context = expr '|-')? lhs = expr steps rhs = expr # StepExprWithMultipleSteps
   ;

premise
   : stepExpr # StepPremise
   | mutableExpr # MutableEntityPremise
   | lhs = expr REWRITE rhs = expr # RewritePremise
   | lhs = expr op = (EQUALS | NOTEQUALS) rhs = expr # BooleanPremise
   | value = expr COLON type = expr # TypePremise
   ;

premises
   : premise+
   ;

ruleDefinition
   : (premises BAR)? conclusion = premise
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

POWN
   : '^N'
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

