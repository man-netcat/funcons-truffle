grammar CBS;

main: index? object+ EOF;

indexLine:
	DATATYPE IDENTIFIER (ALIAS IDENTIFIER)?	# Datatype
	| FUNCON IDENTIFIER (ALIAS IDENTIFIER)?	# Funcon
	| TYPE IDENTIFIER (ALIAS IDENTIFIER)?	# Type
	| ENTITY IDENTIFIER (ALIAS IDENTIFIER)?	# Entity;

index: '[' indexLine+ ']';

object:
	BUILTIN? DATATYPE datatypeDef				# DatatypeDefinition
	| ALIAS aliasDef							# AliasDefinition
	| (BUILTIN | AUXILIARY)? FUNCON funconDef	# FunconDefinition
	| RULE ruleDef								# RuleDefinition
	| METAVARIABLES metavariablesDef			# MetavariablesDefinition
	| ENTITY entityDef							# EntityDefinition
	| BUILTIN? TYPE typeDef						# TypeDefinition
	| ASSERT assertDef							# AssertionDefinition;

assertDef: expr '==' expr;

metavariableDef: (exprs '<:' expr);
metavariablesDef: metavariableDef+;

datatypeDef: expr ('::=' | '<:') expr;

typeDef: expr (REWRITE | '<:') expr | expr;

aliasDef: IDENTIFIER '=' IDENTIFIER;

expr:
	funconExpr				# FunconExpression
	| expr STAR				# ZeroOrMoreExpression
	| expr PLUS				# OneOrMoreExpression
	| expr QMARK			# ZeroOrOneExpression
	| expr POWN				# RepeatNTimes
	| NOT expr				# NotExpression
	| COMPUTES expr			# ComputesExpression
	| expr COMPUTES expr	# ComputesExpression2
	| expr AND expr			# AndExpression
	| expr OR expr			# OrExpression
	| expr COLON expr		# TypeExpression
	| expr NOTEQUALS expr		# NotEqualsExpression
	| expr EQUALS expr		# EqualsExpression
	| nestedExpr			# NestedExpression
	| listExpr				# ListExpression
	| mapExpr				# MapExpression
	| setExpr				# SetExpression
	| tupleExpr				# TupleExpression
	| STRING				# StringLiteral
	| IDENTIFIER			# Identifier
	| NUMBER				# Number;

exprs: (expr (',' expr)*)?;

nestedExpr: '(' expr ')';

funconName: IDENTIFIER;

funconExpr:
	funconName '(' exprs ')'	# FunconCallWithParams
	| funconName '(' exprs ')'	# FunconCallWithExprs
	| funconName expr			# FunconCallWithSingleExpr;
returnType: expr;

rewriteExpr: expr # RewriteExpression;
funconDef:
	funconName ('(' exprs ')')? COLON returnType (
		REWRITE rewriteExpr
	)?;

rewrite: expr REWRITE expr;

trans: ('--' exprs '->' | '--->') NUMBER?;
transitions: trans (';' trans)*;

premise:
	environment? expr transitions expr
	| mutableEntity '--->' mutableEntity
	| rewrite
	| expr;
premises: premise+;
conclusion: premise;

ruleDef: (premises BAR conclusion)	# PremiseConclusion
	| premise						# Transition
	| rewrite						# SimpleRewrite;

mutableEntity: '<' expr ',' expr '>';
environment: expr '|-';
entityDef:
	environment? IDENTIFIER transitions IDENTIFIER
	| mutableEntity '--->' mutableEntity;

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
IDENTIFIER: [a-zA-Z_][a-zA-Z0-9_-]* ('\'')*;