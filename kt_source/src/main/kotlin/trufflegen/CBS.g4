grammar CBS;

@header {
package trufflegen.antlr;
}

root: (index | obj)* EOF;

objectId: DATATYPE | FUNCON | TYPE | ENTITY;

indexLine: (
		objectId name = IDENTIFIER (ALIAS alias = IDENTIFIER)?
	);
index: '[' indexLine+ ']';

obj:
	BUILTIN? DATATYPE datatypeDef (ALIAS aliasDef)* # DatatypeDefinition
	| (BUILTIN | AUXILIARY)? FUNCON funconDef (
		ALIAS aliasDef
		| RULE ruleDef
	)*											# FunconDefinition
	| METAVARIABLES metavariablesDef			# MetavariablesDefinition
	| ENTITY entityDef (ALIAS aliasDef)*		# EntityDefinition
	| BUILTIN? TYPE typeDef (ALIAS aliasDef)*	# TypeDefinition
	| ASSERT assertDef							# AssertDefinition;

assertDef: expr '==' expr;

metavariableDef: (exprs '<:' definition = expr);
metavariablesDef: metavariableDef+;

datatypeDef: name = expr op = ('::=' | '<:') definition = expr;

typeDef:
	name = expr (REWRITE | '<:') definition = expr
	| name = expr;

aliasDef: IDENTIFIER '=' IDENTIFIER;

expr:
	funconExpr														# FunconExpression
	| operand = expr op = (STAR | PLUS | QMARK | POWN)				# SuffixExpression
	| op = NOT operand = expr										# NotExpression
	| op = COMPUTES operand = expr									# UnaryComputesExpression
	| lhs = expr op = COMPUTES rhs = expr							# BinaryComputesExpression
	| lhs = expr op = (AND | OR | EQUALS | NOTEQUALS) rhs = expr	# BinaryOpExpression
	| value = expr op = COLON type = expr							# TypeExpression
	| nestedExpr													# NestedExpression
	| listExpr														# ListExpression
	| mapExpr														# MapExpression
	| setExpr														# SetExpression
	| tupleExpr														# TupleExpression
	| STRING														# String
	| NUMBER														# Number
	| IDENTIFIER													# Identifier;

exprs: (expr (',' expr)*)?;

nestedExpr: '(' expr ')';

arg: value = expr (COLON type = expr);
args: arg (',' arg)*;

funconExpr:
	name = IDENTIFIER '(' args ')'
	| name = IDENTIFIER expr;

param: (value = expr COLON)? type = expr;
params: param (',' param)*;

funconDef:
	name = IDENTIFIER ('(' params ')')? COLON returnType = expr (
		REWRITE rewritesTo = expr
	)?;

action: name = IDENTIFIER polarity = ('!' | '?')? '(' exprs ')';
actions: action (',' action)*;

step: '--->' | '--' actions '->' NUMBER?;
steps: step (';' step)*;

mutableEntity: '<' expr ',' expr '>';
mutableExpr: mutableEntity step mutableEntity;

context: expr '|-';
stepExpr: context? lhs = expr steps rewritesTo = expr;

premise:
	stepExpr											# StepPremise
	| mutableExpr										# MutableEntityPremise
	| lhs = expr REWRITE rewritesTo = expr				# RewritePremise
	| lhs = expr op = (EQUALS | NOTEQUALS) rhs = expr	# BooleanPremise
	| value = expr COLON type = expr					# TypePremise;

premises: premise+;

ruleDef:
	premises BAR conclusion = premise	# TransitionRule
	| premise							# RewriteRule;

entityDef: stepExpr | mutableExpr;

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
SQUOTE: '\'';
IDENTIFIER: [a-zA-Z_][a-zA-Z0-9_-]* SQUOTE*;