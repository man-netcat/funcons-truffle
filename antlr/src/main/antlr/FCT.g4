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
   : 'funcon-term' ':' funcon ';'
   ;

funcon
   : funconName '(' exprs ')'
   | funconName expr
   ; // Single param funcons can omit parentheses (e.g. 'print')
   
funconName
   : IDENTIFIER
   ;

expr
   : unOp expr # UnopExpression
   | lhs = expr binOp rhs = expr # BinOpExpression
   | funcon # FunconExpression
   | listExpr # ListExpression
   | mapExpr # MapExpression
   | setExpr # SetExpression
   | tupleExpr # TupleExpression
   | terminal # TerminalExpression
   ;

exprs
   : (expr (',' expr)*)?
   ;

terminal
   : STRING
   | IDENTIFIER
   | NUMBER
   | EMPTY
   ;

terminals
   : (terminal (',' terminal)*)?
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
   : key = terminal '|->' value = terminal
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
   | terminal # InputValue
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
   : [a-zA-Z_] [a-zA-Z0-9_-]*
   ;

NUMBER
   : [0-9]+
   ;

WS
   : [ \t\r\n]+ -> skip
   ;

