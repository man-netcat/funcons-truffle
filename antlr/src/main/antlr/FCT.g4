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
   : expr # SingleArgs
   | # NoArgs
   ;


expr
   : name = IDENTIFIER args # FunconExpression
   | '(' sequenceExpr ')' # SequenceExpression
   | unOp expr # UnopExpression
   | lhs = expr binOp rhs = expr # BinOpExpression
   | listExpr # ListExpression
   | mapExpr # MapExpression
   | setExpr # SetExpression
   | tupleExpr # TupleExpression
   | terminalValue # TerminalExpression
   ;

sequenceExpr
   : (expr (',' expr)*)?
   ;

terminalValue
   : STRING # String
   | NUMBER # Number
   ;

terminals
   : (terminalValue (',' terminalValue)*)?
   ;

listExpr
   : '[' sequenceExpr ']'
   ;

mapExpr
   : '{' pairs '}'
   ;

setExpr
   : '{' sequenceExpr '}'
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
   : 'tuple(' sequenceExpr ')'
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
   : name=IDENTIFIER ':' expr ';'
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

