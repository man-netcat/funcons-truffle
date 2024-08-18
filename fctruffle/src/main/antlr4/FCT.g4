grammar FCT;

topLevel: generalBlock inputsBlock? testsBlock? EOF;

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

tests: resultTerm | standardOut;

resultTerm: 'result-term' ':' expr ';';
standardOut: 'standard-out' ':' '[' exprs ']' ';';

binOp: AND | OR;

unOp: NOT;

NOT: '~';
AND: '&';
OR: '|';

EMPTY: '(' WS? ')';
COMMENT: '//' ~[\r\n]* -> skip;
STRING: '"' .*? '"';
IDENTIFIER: [a-zA-Z_][a-zA-Z0-9_-]*;
NUMBER: [0-9]+;
WS: [ \t\r\n]+ -> skip;