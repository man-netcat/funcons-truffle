// Generated from FCT.g4 by ANTLR 4.13.2
package trufflegen.antlr4;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link FCTParser}.
 */
public interface FCTListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link FCTParser#main}.
	 * @param ctx the parse tree
	 */
	void enterMain(FCTParser.MainContext ctx);
	/**
	 * Exit a parse tree produced by {@link FCTParser#main}.
	 * @param ctx the parse tree
	 */
	void exitMain(FCTParser.MainContext ctx);
	/**
	 * Enter a parse tree produced by {@link FCTParser#generalBlock}.
	 * @param ctx the parse tree
	 */
	void enterGeneralBlock(FCTParser.GeneralBlockContext ctx);
	/**
	 * Exit a parse tree produced by {@link FCTParser#generalBlock}.
	 * @param ctx the parse tree
	 */
	void exitGeneralBlock(FCTParser.GeneralBlockContext ctx);
	/**
	 * Enter a parse tree produced by {@link FCTParser#testsBlock}.
	 * @param ctx the parse tree
	 */
	void enterTestsBlock(FCTParser.TestsBlockContext ctx);
	/**
	 * Exit a parse tree produced by {@link FCTParser#testsBlock}.
	 * @param ctx the parse tree
	 */
	void exitTestsBlock(FCTParser.TestsBlockContext ctx);
	/**
	 * Enter a parse tree produced by {@link FCTParser#funconTerm}.
	 * @param ctx the parse tree
	 */
	void enterFunconTerm(FCTParser.FunconTermContext ctx);
	/**
	 * Exit a parse tree produced by {@link FCTParser#funconTerm}.
	 * @param ctx the parse tree
	 */
	void exitFunconTerm(FCTParser.FunconTermContext ctx);
	/**
	 * Enter a parse tree produced by {@link FCTParser#funcon}.
	 * @param ctx the parse tree
	 */
	void enterFuncon(FCTParser.FunconContext ctx);
	/**
	 * Exit a parse tree produced by {@link FCTParser#funcon}.
	 * @param ctx the parse tree
	 */
	void exitFuncon(FCTParser.FunconContext ctx);
	/**
	 * Enter a parse tree produced by {@link FCTParser#funconName}.
	 * @param ctx the parse tree
	 */
	void enterFunconName(FCTParser.FunconNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link FCTParser#funconName}.
	 * @param ctx the parse tree
	 */
	void exitFunconName(FCTParser.FunconNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link FCTParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterExpr(FCTParser.ExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link FCTParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitExpr(FCTParser.ExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link FCTParser#value}.
	 * @param ctx the parse tree
	 */
	void enterValue(FCTParser.ValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link FCTParser#value}.
	 * @param ctx the parse tree
	 */
	void exitValue(FCTParser.ValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link FCTParser#mapExpr}.
	 * @param ctx the parse tree
	 */
	void enterMapExpr(FCTParser.MapExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link FCTParser#mapExpr}.
	 * @param ctx the parse tree
	 */
	void exitMapExpr(FCTParser.MapExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link FCTParser#pair}.
	 * @param ctx the parse tree
	 */
	void enterPair(FCTParser.PairContext ctx);
	/**
	 * Exit a parse tree produced by {@link FCTParser#pair}.
	 * @param ctx the parse tree
	 */
	void exitPair(FCTParser.PairContext ctx);
	/**
	 * Enter a parse tree produced by {@link FCTParser#tupleExpr}.
	 * @param ctx the parse tree
	 */
	void enterTupleExpr(FCTParser.TupleExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link FCTParser#tupleExpr}.
	 * @param ctx the parse tree
	 */
	void exitTupleExpr(FCTParser.TupleExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link FCTParser#tests}.
	 * @param ctx the parse tree
	 */
	void enterTests(FCTParser.TestsContext ctx);
	/**
	 * Exit a parse tree produced by {@link FCTParser#tests}.
	 * @param ctx the parse tree
	 */
	void exitTests(FCTParser.TestsContext ctx);
	/**
	 * Enter a parse tree produced by {@link FCTParser#resultTest}.
	 * @param ctx the parse tree
	 */
	void enterResultTest(FCTParser.ResultTestContext ctx);
	/**
	 * Exit a parse tree produced by {@link FCTParser#resultTest}.
	 * @param ctx the parse tree
	 */
	void exitResultTest(FCTParser.ResultTestContext ctx);
	/**
	 * Enter a parse tree produced by {@link FCTParser#standardOutTest}.
	 * @param ctx the parse tree
	 */
	void enterStandardOutTest(FCTParser.StandardOutTestContext ctx);
	/**
	 * Exit a parse tree produced by {@link FCTParser#standardOutTest}.
	 * @param ctx the parse tree
	 */
	void exitStandardOutTest(FCTParser.StandardOutTestContext ctx);
}