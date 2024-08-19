grammar FCT;

root: generalBlock inputsBlock? testsBlock? EOF;

generalBlock: 'general' '{' funconTerm '}';

testsBlock: 'tests' '{' tests+ '}';

inputsBlock: 'inputs' '{' standardIn+ '}';

funconTerm: 'funcon-term' ':' funcon ';';

funcon:
	funconName '(' exprs ')'
	| funconName expr; // Single param funcons can omit parentheses (e.g. 'print')

funconName: IDENTIFIER;

expr:
	unOp expr
	| expr binOp expr
	| funcon
	| listExpr
	| mapExpr
	| setExpr
	| tupleExpr
	| terminal;

exprs: (expr (',' expr)*)?;

terminal: STRING | IDENTIFIER | NUMBER | EMPTY;

terminals: (terminal (',' terminal)*)?;

listExpr: '[' exprs ']';

mapExpr: '{' pairs '}';

setExpr: '{' exprs '}';

pair: expr '|->' expr;
termPair: terminal '|->' terminal;

pairs: (pair (',' pair)*)?;
termPairs: (termPair (',' termPair)*)?;

tupleExpr: 'tuple(' exprs ')';

standardIn: 'standard-in' ':' inputValue ';';

inputValue:
	'(' terminals ')'
	| '[' terminals ']'
	| '{' termPairs
	| terminals '}'
	| terminal;

tests: resultTerm | standardOut | store;

resultTerm: 'result-term' ':' expr ';';
store: 'store' ':' expr ';';
standardOut: 'standard-out' ':' '[' exprs ']' ';';

binOp: AND | OR | COMPUTE;

unOp: NOT | COMPUTE;

NOT: '~';
AND: '&';
OR: '|';
COMPUTE: '=>';

EMPTY: '(' WS? ')';
COMMENT: '//' ~[\r\n]* -> skip;
STRING: '"' .*? '"';
IDENTIFIER: [a-zA-Z_][a-zA-Z0-9_-]*;
NUMBER: [0-9]+;
WS: [ \t\r\n]+ -> skip;
