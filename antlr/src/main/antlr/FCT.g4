grammar FCT;

@header {
    package fct;
}

root
   : expr EOF # FCTFile
   | generalBlock (inputsBlock)? (testsBlock)? EOF # ConfigFile
   ;

generalBlock
   : GENERAL '{' funconTerm generalOption* '}'
   ;

/*
  Options in the `general` block are carried by configs that the unstable corpora
  ship (`refocus`, `display-mutable-entity`, `seed`, `non-deterministic`). Nothing
  reads them: they are accepted so those configs parse, and a config that actually
  depends on one fails on the expectation it declares. A value may be a string, a
  number, or a bare word such as `false`, and there may be more than one, so this is
  a repetition rather than the single optional pair it was.
 */
generalOption
   : IDENTIFIER ':' (STRING | NUMBER | IDENTIFIER) ';'
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
   | '{' '}' # EmptySet
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