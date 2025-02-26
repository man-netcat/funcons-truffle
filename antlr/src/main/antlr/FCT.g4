grammar FCT;

@header {
    package fct;
}

root
   : generalBlock inputsBlock? testsBlock? EOF
   ;

generalBlock
   : 'general' '{' funconTerm '}'
   ;

testsBlock
   : 'tests' '{' tests+ '}'
   ;

inputsBlock
   : 'inputs' '{' standardIn+ '}'
   ;

funconTerm
   : 'funcon-term' ':' expr ';'
   ;

args
   : '(' exprs ')' # MultipleArgs
   | expr # SingleArgs
   | # NoArgs
   ;


expr
   : name = IDENTIFIER args # FunconExpression
   | unOp expr # UnopExpression
   | lhs = expr binOp rhs = expr # BinOpExpression
   | listExpr # ListExpression
   | mapExpr # MapExpression
   | setExpr # SetExpression
   | tupleExpr # TupleExpression
   | terminalValue # TerminalExpression
   ;

exprs
   : (expr (',' expr)*)?
   ;

terminalValue
   : STRING # String
   | NUMBER # Number
   | EMPTY # Empty
   ;

terminals
   : (terminalValue (',' terminalValue)*)?
   ;

listExpr
   : '[' exprs ']'
   ;

mapExpr
   : '{' pairs '}'
   ;

setExpr
   : '{' exprs '}'
   ;

pair
   : key = expr '|->' value = expr
   ;

termPair
   : key = terminalValue '|->' value = terminalValue
   ;

pairs
   : (pair (',' pair)*)?
   ;

termPairs
   : (termPair (',' termPair)*)?
   ;

tupleExpr
   : 'tuple(' exprs ')'
   ;

standardIn
   : 'standard-in' ':' input ';'
   ;

input
   : '(' terminals ')' # InputTuple
   | '[' terminals ']' # InputList
   | '{' termPairs '}' # InputMap
   | '{' terminals '}' # InputSet
   | terminalValue # InputValue
   ;

tests
   : resultTerm
   | standardOut
   | store
   ;

resultTerm
   : 'result-term' ':' expr ';'
   ;

store
   : 'store' ':' expr ';'
   ;

standardOut
   : 'standard-out' ':' '[' exprs ']' ';'
   ;

binOp
   : AND
   | OR
   | COMPUTE
   ;

unOp
   : NOT
   | COMPUTE
   ;

NOT
   : '~'
   ;

AND
   : '&'
   ;

OR
   : '|'
   ;

COMPUTE
   : '=>'
   ;

EMPTY
   : '(' WS? ')'
   ;

COMMENT
   : '//' ~ [\r\n]* -> skip
   ;

STRING
   : '"' .*? '"'
   ;

IDENTIFIER
   : [a-zA-Z_] [a-zA-Z0-9-]*
   ;

NUMBER
   : [0-9]+
   ;

WS
   : [ \t\r\n]+ -> skip
   ;

