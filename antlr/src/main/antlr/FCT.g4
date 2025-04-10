grammar FCT;

@header {
    package fct;
}

root
   : generalBlock (inputsBlock)? (testsBlock)? EOF
   ;

generalBlock
   : GENERAL '{' funconTerm '}'
   ;

testsBlock
   : TESTS '{' resultTerm standardOut? store? '}'
   ;

inputsBlock
   : INPUTS '{' standardIn '}'
   ;

funconTerm
   : FUNCON_TERM ':' expr ';'
   ;

resultTerm
   : RESULT_TERM ':' expr ';'
   ;

standardOut
   : STANDARD_OUT ':' expr ';'
   ;

standardIn
   : STANDARD_IN ':' input ';'
   ;

store
   : STORE ':' expr ';'
   ;

args
   : expr # SingleArgs
   | # NoArgs
   ;


expr
   : name = IDENTIFIER args # FunconExpression
   | '(' sequenceExpr ')' # SequenceExpression
   | unOp operand = expr # UnopExpression
   | lhs = expr binOp rhs = expr # BinOpExpression
   | listExpr # ListExpression
   | '{' WS* '}' # EmptySet
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

input
   : '(' terminals ')' # InputTuple
   | '[' terminals ']' # InputList
   | '{' termPairs '}' # InputMap
   | '{' terminals '}' # InputSet
   | terminalValue # InputValue
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

GENERAL: 'general';
INPUTS: 'inputs';
TESTS: 'tests';
FUNCON_TERM: 'funcon-term';
STANDARD_IN: 'standard-in';
RESULT_TERM: 'result-term';
STANDARD_OUT: 'standard-out';
STORE: 'store';

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