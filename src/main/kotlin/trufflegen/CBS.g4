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

index
   : '[' (definitionId name = IDENTIFIER (ALIAS alias = IDENTIFIER)?)+ ']'
   ;

obj
   : (modifier = BUILTIN? DATATYPE name = expr op = ('::=' | '<:') expr ('|' expr)* (ALIAS aliasDefinition | ASSERT assertDefinition)*) # DatatypeDefinition
   | (modifier = (BUILTIN | AUXILIARY)? FUNCON name = IDENTIFIER ('(' params ')')? COLON returnType = expr (REWRITE rewritesTo = expr)? (ALIAS aliasDefinition | RULE ruleDefinition | ASSERT assertDefinition)*) # FunconDefinition
   | METAVARIABLES (exprs '<:' definition = expr)+ # MetavariablesDefinition
   | ENTITY (stepExpr | mutableExpr) (ALIAS aliasDefinition)* # EntityDefinition
   | (modifier = BUILTIN? TYPE (name = expr (REWRITE | '<:') definition = expr | name = expr) (ALIAS aliasDefinition)*) # TypeDefinition
   ;

assertDefinition
   : expr '==' expr
   ;

aliasDefinition
   : name = IDENTIFIER '=' original = IDENTIFIER
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
   | '(' expr ')' # NestedExpression
   | '[' exprs? ']' # ListExpression
   | '{' pairs '}' # MapExpression
   | '{' exprs? '}' # SetExpression
   | '(' exprs? ')' # TupleExpression
   | string = STRING # String
   | value = NUMBER # Number
   | varname = VARIABLE rewriteSteps = SQUOTE+ # VariableStep
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

action
   : name = IDENTIFIER polarity = ('!' | '?')? '(' exprs? ')'
   ;

actions
   : action (',' action)*
   ;

step
   : '--->' NUMBER?
   | '--' actions '->' NUMBER?
   ;

steps
   : step (';' step)*
   ;

mutableEntity
   : '<' expr ',' expr '>'
   ;

mutableExpr
   : mutableEntity step mutableEntity
   ;

stepExpr
   : (context = expr '|-')? lhs = expr steps rewritesTo = expr
   ;

premise
   : stepExpr # StepPremise
   | mutableExpr # MutableEntityPremise
   | lhs = expr REWRITE rewritesTo = expr # RewritePremise
   | lhs = expr op = (EQUALS | NOTEQUALS) rhs = expr # BooleanPremise
   | value = expr COLON type = expr # TypePremise
   ;

premises
   : premise+
   ;

ruleDefinition
   : premises BAR conclusion = premise # TransitionRule
   | premise # RewriteRule
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

SQUOTE
   : '\''
   ;

VARIABLE
   : [A-Z] [a-zA-Z0-9_-]*
   ;

IDENTIFIER
   : [A-Za-z_] [a-zA-Z0-9_-]*
   ;

