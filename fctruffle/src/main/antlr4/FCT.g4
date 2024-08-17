grammar FCT;

main: generalBlock testsBlock? EOF;

generalBlock: 'general' '{' funconTerm '}' ;

testsBlock: 'tests' '{' tests+ '}' ;

funconTerm: 'funcon-term' ':' expr ';' ;

funcon
    : funconName '(' exprs ')'
    | funconName expr // Single param funcons can omit parentheses
    ;

funconName: IDENTIFIER ;

expr
    : unOp expr
    | expr binOp expr
    | funcon
    | listExpr
    | mapExpr
    | setExpr
    | tupleExpr
    | terminal
    ;

exprs : (expr (',' expr)*)? ;

terminal
    : STRING
    | IDENTIFIER
    | NUMBER
    | EMPTY
    ;

listExpr : '[' exprs ']' ;

mapExpr : '{' pairs '}' ;

setExpr : '{' exprs '}' ;

pair: expr '|->' expr ;

pairs : (pair (',' pair)*)? ;

tupleExpr: 'tuple(' exprs ')' ;

tests: resultTest | standardOutTest ;

resultTest: 'result-term' ':' expr ';' ;
standardOutTest: 'standard-out' ':' '[' exprs ']' ';' ;

binOp
    : AND
    | OR
    ;

unOp
    : NOT
    ;

NOT: '~' ;
AND: '&' ;
OR: '|' ;

EMPTY: '(' WS? ')' ;
COMMENT: '//' ~[\r\n]* -> skip ;
STRING: '"' .*? '"' ;
IDENTIFIER: [a-zA-Z_][a-zA-Z0-9_-]* ;
NUMBER: [0-9]+ ;
WS: [ \t\r\n]+ -> skip ;
