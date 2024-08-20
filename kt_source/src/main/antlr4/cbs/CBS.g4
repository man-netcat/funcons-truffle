grammar CBS;
fragment SQUOTE: '\'';

root: (index | object)* EOF;

objectId: DATATYPE | FUNCON | TYPE | ENTITY;

index: '[' (objectId IDENTIFIER ( ALIAS IDENTIFIER)?)+ ']';

object:
	BUILTIN? DATATYPE datatypeDef (ALIAS aliasDef)*
	| (BUILTIN | AUXILIARY)? FUNCON funconDef (ALIAS aliasDef | RULE ruleDef)*
	| METAVARIABLES metavariablesDef
	| ENTITY entityDef (ALIAS aliasDef)*
	| BUILTIN? TYPE typeDef (ALIAS aliasDef)*
	| ASSERT assertDef;

assertDef: expr '==' expr;

metavariableDef: (exprs '<:' expr);
metavariablesDef: metavariableDef+;

datatypeDef: expr ('::=' | '<:') expr;

typeDef: expr (REWRITE | '<:') expr | expr;

aliasDef: IDENTIFIER '=' IDENTIFIER;

expr:
	 funconExpr
	|expr STAR
	| expr PLUS
	| expr QMARK
	| expr POWN
	| NOT expr
	| COMPUTES expr
	| expr COMPUTES expr
	| expr AND expr
	| expr OR expr
	| expr COLON expr
	| expr NOTEQUALS expr
	| expr EQUALS expr
	| nestedExpr
	| listExpr
	| mapExpr
	| setExpr
	| tupleExpr
	| STRING
	| NUMBER
	| IDENTIFIER;

exprs: (expr (',' expr)*)?;

nestedExpr: '(' expr ')';

funconExpr: IDENTIFIER '(' exprs ')' | IDENTIFIER expr;

returnType: expr;

funconDef:
	IDENTIFIER ('(' exprs ')')? COLON returnType (REWRITE expr)?;

action: IDENTIFIER ('!' | '?')? '(' exprs ')';
actions: action (',' action)*;

step: '--->' | '--' actions '->' NUMBER?;
steps: step (';' step)*;

mutableEntity: '<' expr ',' expr '>';
mutableExpr: mutableEntity step mutableEntity;

context: expr '|-';
stepExpr: context? expr steps expr;

typeExpr: expr COLON expr;

boolExpr: expr (EQUALS | NOTEQUALS) expr;

rewrite: expr REWRITE expr;

premise: stepExpr | mutableExpr | rewrite | boolExpr | typeExpr;

transition: premise+ BAR premise;
ruleDef: transition | premise;

entityDef:
	stepExpr | mutableExpr;

listExpr: '[' exprs ']';

mapExpr: '{' pairs '}';
setExpr: '{' exprs '}';
pair: expr '|->' expr;
pairs: (pair (',' pair)*)?;

tupleExpr: '(' exprs ')';

STAR: '*';
PLUS: '+';
QMARK: '?';
POWN: '^N';
COMPUTES: '=>';
NOT: '~';
AND: '&';
OR: '|';
COLON: ':';
NOTEQUALS: '=/=';
EQUALS: '==';

WS: [ \t\r\n]+ -> skip;
COMMENT: '#' ('#' | ~[\r\n])* -> skip;
BLOCKCOMMENT: '/*' .*? '*/' -> skip;
STRING: '"' .*? '"';
NUMBER: '-'? [0-9]+ ('.' [0-9]+)?;
DATATYPE: 'Datatype';
FUNCON: 'Funcon';
ALIAS: 'Alias';
RULE: 'Rule';
ENTITY: 'Entity';
TYPE: 'Type';
METAVARIABLES: 'Meta-variables';
BUILTIN: 'Built-in';
AUXILIARY: 'Auxiliary';
ASSERT: 'Assert';
BAR: '----' '-'*;
REWRITE: '~>';
//FUNCONID: [a-z][a-z0-9-]+;
IDENTIFIER: [a-zA-Z_][a-zA-Z0-9_-]* SQUOTE*;
