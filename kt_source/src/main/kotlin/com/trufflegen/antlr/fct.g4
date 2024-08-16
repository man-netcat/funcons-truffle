grammar fct;

parse: generalBlock testsBlock;

generalBlock: 'general' '{' funconTerm '}' ;
testsBlock: 'tests' '{' tests+ '}' ;

funconTerm: 'funcon-term' ':' expr ';' ;

funcon
    : funconName '(' (expr (',' expr)*)? ')'
    | funconName expr
    ;

funconName: IDENTIFIER;

expr
    : funcon
    | value
    | funconName
    | '(' expr ')'
    ;

value
    : STRING
    | IDENTIFIER
    | NUMBER
    | mapExpr
    | tupleExpr
    ;

mapExpr: '{' (pair (',' pair)*)? '}' ;
pair: STRING '|->' value ;

tupleExpr: 'tuple(' (expr (',' expr)*)? ')' ;

tests: resultTest | standardOutTest ;

resultTest: 'result-term' ':' 'null-value' ';' ;
standardOutTest: 'standard-out' ':' '[' value (',' value)* ']' ';' ;

STRING: '"' .*? '"' ;
IDENTIFIER: [a-zA-Z_][a-zA-Z0-9_]* ;
NUMBER: [0-9]+ ;
WS: [ \t\r\n]+ -> skip ;
