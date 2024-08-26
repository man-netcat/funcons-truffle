grammar CBS;

@header {
package trufflegen.antlr;
}

root: (index | obj)* EOF;

objectId: DATATYPE | FUNCON | TYPE | ENTITY;

indexLine: (
		objectId name = FUNCONID (ALIAS alias = FUNCONID)?
	);
index: '[' indexLine+ ']';

obj:
	BUILTIN? DATATYPE datatypeObj (ALIAS aliasObj)* # DatatypeObject
	| (BUILTIN | AUXILIARY)? FUNCON funconObj (
		ALIAS aliasObj
		| RULE ruleObj
	)*											# FunconObject
	| METAVARIABLES metavariablesObj			# MetavariablesObject
	| ENTITY entityObj (ALIAS aliasObj)*		# EntityObject
	| BUILTIN? TYPE typeObj (ALIAS aliasObj)*	# TypeObject
	| ASSERT assertObj							# AssertObject;

assertObj: expr '==' expr;

metavariableDef: (exprs '<:' definition = expr);
metavariablesObj: metavariableDef+;

datatypeDefs: (expr ('|' expr)*)?;
datatypeObj: name = expr op = ('::=' | '<:') datatypeDefs;

typeObj:
	name = expr (REWRITE | '<:') definition = expr
	| name = expr;

aliasObj: name=FUNCONID '=' original=FUNCONID;

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

args
    : '(' exprs ')' # MultipleArgs
    | expr          # SingleArgs
    |               # NoArgs;

funconExpr:
	name = FUNCONID args;

param: (value = expr COLON)? type = expr;
params: param (',' param)*;

funconObj:
	name = FUNCONID ('(' params ')')? COLON returnType = expr (
		REWRITE rewritesTo = expr
	)?;

action: name = FUNCONID polarity = ('!' | '?')? '(' exprs ')';
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

ruleObj:
	premises BAR conclusion = premise	# TransitionRule
	| premise							# RewriteRule;

entityObj: stepExpr | mutableExpr;

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
FUNCONID: [a-z][a-zA-Z0-9-]* SQUOTE*;
IDENTIFIER: [A-Za-z_][a-zA-Z0-9_-]* SQUOTE*;
