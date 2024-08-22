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

datatypeDef: expr op = ('::=' | '<:') definition = expr;

typeDef: expr (REWRITE | '<:') expr | expr;

aliasDef: IDENTIFIER '=' IDENTIFIER;

expr:
	funconExpr								# FunconExpression
	| operand = expr mod = STAR				# StarExpression
	| operand = expr mod = PLUS				# PlusExpression
	| operand = expr mod = QMARK			# QuestionMarkExpression
	| operand = expr mod = POWN				# PowerNExpression
	| op = NOT operand = expr				# NotExpression
	| op = COMPUTES operand = expr			# UnaryComputesExpression
	| lhs = expr op = COMPUTES rhs = expr	# BinaryComputesExpression
	| lhs = expr op = AND rhs = expr		# AndExpression
	| lhs = expr op = OR rhs = expr			# OrExpression
	| value = expr op = COLON type = expr	# TypeExpression
	| lhs = expr op = NOTEQUALS rhs = expr	# NotEqualsExpression
	| lhs = expr op = EQUALS rhs = expr		# EqualsExpression
	| nestedExpr							# NestedExpression
	| listExpr								# ListExpression
	| mapExpr								# MapExpression
	| setExpr								# SetExpression
	| tupleExpr								# TupleExpression
	| STRING								# String
	| NUMBER								# Number
	| IDENTIFIER							# Identifier;

exprs: (expr (',' expr)*)?;

nestedExpr: '(' expr ')';

funconExpr:
	name = IDENTIFIER '(' args = exprs ')'
	| name = IDENTIFIER arg = expr;

funconDef:
	name = IDENTIFIER ('(' params = exprs ')')? COLON returnType = expr (
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