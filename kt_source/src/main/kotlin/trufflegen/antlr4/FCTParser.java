// Generated from FCT.g4 by ANTLR 4.13.2
package trufflegen.antlr4;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class FCTParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, T__11=12, T__12=13, T__13=14, T__14=15, T__15=16, T__16=17, 
		STRING=18, IDENTIFIER=19, NUMBER=20, WS=21;
	public static final int
		RULE_main = 0, RULE_generalBlock = 1, RULE_testsBlock = 2, RULE_funconTerm = 3, 
		RULE_funcon = 4, RULE_funconName = 5, RULE_expr = 6, RULE_value = 7, RULE_mapExpr = 8, 
		RULE_pair = 9, RULE_tupleExpr = 10, RULE_tests = 11, RULE_resultTest = 12, 
		RULE_standardOutTest = 13;
	private static String[] makeRuleNames() {
		return new String[] {
			"main", "generalBlock", "testsBlock", "funconTerm", "funcon", "funconName", 
			"expr", "value", "mapExpr", "pair", "tupleExpr", "tests", "resultTest", 
			"standardOutTest"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'general'", "'{'", "'}'", "'tests'", "'funcon-term'", "':'", "';'", 
			"'('", "','", "')'", "'|->'", "'tuple('", "'result-term'", "'null-value'", 
			"'standard-out'", "'['", "']'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, "STRING", "IDENTIFIER", "NUMBER", 
			"WS"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "FCT.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public FCTParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class MainContext extends ParserRuleContext {
		public GeneralBlockContext generalBlock() {
			return getRuleContext(GeneralBlockContext.class,0);
		}
		public TestsBlockContext testsBlock() {
			return getRuleContext(TestsBlockContext.class,0);
		}
		public MainContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_main; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).enterMain(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).exitMain(this);
		}
	}

	public final MainContext main() throws RecognitionException {
		MainContext _localctx = new MainContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_main);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(28);
			generalBlock();
			setState(29);
			testsBlock();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class GeneralBlockContext extends ParserRuleContext {
		public FunconTermContext funconTerm() {
			return getRuleContext(FunconTermContext.class,0);
		}
		public GeneralBlockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_generalBlock; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).enterGeneralBlock(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).exitGeneralBlock(this);
		}
	}

	public final GeneralBlockContext generalBlock() throws RecognitionException {
		GeneralBlockContext _localctx = new GeneralBlockContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_generalBlock);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(31);
			match(T__0);
			setState(32);
			match(T__1);
			setState(33);
			funconTerm();
			setState(34);
			match(T__2);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TestsBlockContext extends ParserRuleContext {
		public List<TestsContext> tests() {
			return getRuleContexts(TestsContext.class);
		}
		public TestsContext tests(int i) {
			return getRuleContext(TestsContext.class,i);
		}
		public TestsBlockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_testsBlock; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).enterTestsBlock(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).exitTestsBlock(this);
		}
	}

	public final TestsBlockContext testsBlock() throws RecognitionException {
		TestsBlockContext _localctx = new TestsBlockContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_testsBlock);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(36);
			match(T__3);
			setState(37);
			match(T__1);
			setState(39); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(38);
				tests();
				}
				}
				setState(41); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==T__12 || _la==T__14 );
			setState(43);
			match(T__2);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class FunconTermContext extends ParserRuleContext {
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public FunconTermContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_funconTerm; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).enterFunconTerm(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).exitFunconTerm(this);
		}
	}

	public final FunconTermContext funconTerm() throws RecognitionException {
		FunconTermContext _localctx = new FunconTermContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_funconTerm);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(45);
			match(T__4);
			setState(46);
			match(T__5);
			setState(47);
			expr();
			setState(48);
			match(T__6);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class FunconContext extends ParserRuleContext {
		public FunconNameContext funconName() {
			return getRuleContext(FunconNameContext.class,0);
		}
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public FunconContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_funcon; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).enterFuncon(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).exitFuncon(this);
		}
	}

	public final FunconContext funcon() throws RecognitionException {
		FunconContext _localctx = new FunconContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_funcon);
		int _la;
		try {
			setState(67);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,3,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(50);
				funconName();
				setState(51);
				match(T__7);
				setState(60);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 1839364L) != 0)) {
					{
					setState(52);
					expr();
					setState(57);
					_errHandler.sync(this);
					_la = _input.LA(1);
					while (_la==T__8) {
						{
						{
						setState(53);
						match(T__8);
						setState(54);
						expr();
						}
						}
						setState(59);
						_errHandler.sync(this);
						_la = _input.LA(1);
					}
					}
				}

				setState(62);
				match(T__9);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(64);
				funconName();
				setState(65);
				expr();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class FunconNameContext extends ParserRuleContext {
		public TerminalNode IDENTIFIER() { return getToken(FCTParser.IDENTIFIER, 0); }
		public FunconNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_funconName; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).enterFunconName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).exitFunconName(this);
		}
	}

	public final FunconNameContext funconName() throws RecognitionException {
		FunconNameContext _localctx = new FunconNameContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_funconName);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(69);
			match(IDENTIFIER);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ExprContext extends ParserRuleContext {
		public FunconContext funcon() {
			return getRuleContext(FunconContext.class,0);
		}
		public ValueContext value() {
			return getRuleContext(ValueContext.class,0);
		}
		public FunconNameContext funconName() {
			return getRuleContext(FunconNameContext.class,0);
		}
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public ExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).enterExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).exitExpr(this);
		}
	}

	public final ExprContext expr() throws RecognitionException {
		ExprContext _localctx = new ExprContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_expr);
		try {
			setState(78);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,4,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(71);
				funcon();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(72);
				value();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(73);
				funconName();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(74);
				match(T__7);
				setState(75);
				expr();
				setState(76);
				match(T__9);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ValueContext extends ParserRuleContext {
		public TerminalNode STRING() { return getToken(FCTParser.STRING, 0); }
		public TerminalNode IDENTIFIER() { return getToken(FCTParser.IDENTIFIER, 0); }
		public TerminalNode NUMBER() { return getToken(FCTParser.NUMBER, 0); }
		public MapExprContext mapExpr() {
			return getRuleContext(MapExprContext.class,0);
		}
		public TupleExprContext tupleExpr() {
			return getRuleContext(TupleExprContext.class,0);
		}
		public ValueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_value; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).enterValue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).exitValue(this);
		}
	}

	public final ValueContext value() throws RecognitionException {
		ValueContext _localctx = new ValueContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_value);
		try {
			setState(85);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STRING:
				enterOuterAlt(_localctx, 1);
				{
				setState(80);
				match(STRING);
				}
				break;
			case IDENTIFIER:
				enterOuterAlt(_localctx, 2);
				{
				setState(81);
				match(IDENTIFIER);
				}
				break;
			case NUMBER:
				enterOuterAlt(_localctx, 3);
				{
				setState(82);
				match(NUMBER);
				}
				break;
			case T__1:
				enterOuterAlt(_localctx, 4);
				{
				setState(83);
				mapExpr();
				}
				break;
			case T__11:
				enterOuterAlt(_localctx, 5);
				{
				setState(84);
				tupleExpr();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class MapExprContext extends ParserRuleContext {
		public List<PairContext> pair() {
			return getRuleContexts(PairContext.class);
		}
		public PairContext pair(int i) {
			return getRuleContext(PairContext.class,i);
		}
		public MapExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mapExpr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).enterMapExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).exitMapExpr(this);
		}
	}

	public final MapExprContext mapExpr() throws RecognitionException {
		MapExprContext _localctx = new MapExprContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_mapExpr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(87);
			match(T__1);
			setState(96);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==STRING) {
				{
				setState(88);
				pair();
				setState(93);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__8) {
					{
					{
					setState(89);
					match(T__8);
					setState(90);
					pair();
					}
					}
					setState(95);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(98);
			match(T__2);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class PairContext extends ParserRuleContext {
		public TerminalNode STRING() { return getToken(FCTParser.STRING, 0); }
		public ValueContext value() {
			return getRuleContext(ValueContext.class,0);
		}
		public PairContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_pair; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).enterPair(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).exitPair(this);
		}
	}

	public final PairContext pair() throws RecognitionException {
		PairContext _localctx = new PairContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_pair);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(100);
			match(STRING);
			setState(101);
			match(T__10);
			setState(102);
			value();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TupleExprContext extends ParserRuleContext {
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TupleExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_tupleExpr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).enterTupleExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).exitTupleExpr(this);
		}
	}

	public final TupleExprContext tupleExpr() throws RecognitionException {
		TupleExprContext _localctx = new TupleExprContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_tupleExpr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(104);
			match(T__11);
			setState(113);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 1839364L) != 0)) {
				{
				setState(105);
				expr();
				setState(110);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__8) {
					{
					{
					setState(106);
					match(T__8);
					setState(107);
					expr();
					}
					}
					setState(112);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(115);
			match(T__9);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TestsContext extends ParserRuleContext {
		public ResultTestContext resultTest() {
			return getRuleContext(ResultTestContext.class,0);
		}
		public StandardOutTestContext standardOutTest() {
			return getRuleContext(StandardOutTestContext.class,0);
		}
		public TestsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_tests; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).enterTests(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).exitTests(this);
		}
	}

	public final TestsContext tests() throws RecognitionException {
		TestsContext _localctx = new TestsContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_tests);
		try {
			setState(119);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__12:
				enterOuterAlt(_localctx, 1);
				{
				setState(117);
				resultTest();
				}
				break;
			case T__14:
				enterOuterAlt(_localctx, 2);
				{
				setState(118);
				standardOutTest();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ResultTestContext extends ParserRuleContext {
		public ResultTestContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_resultTest; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).enterResultTest(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).exitResultTest(this);
		}
	}

	public final ResultTestContext resultTest() throws RecognitionException {
		ResultTestContext _localctx = new ResultTestContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_resultTest);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(121);
			match(T__12);
			setState(122);
			match(T__5);
			setState(123);
			match(T__13);
			setState(124);
			match(T__6);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class StandardOutTestContext extends ParserRuleContext {
		public List<ValueContext> value() {
			return getRuleContexts(ValueContext.class);
		}
		public ValueContext value(int i) {
			return getRuleContext(ValueContext.class,i);
		}
		public StandardOutTestContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_standardOutTest; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).enterStandardOutTest(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FCTListener ) ((FCTListener)listener).exitStandardOutTest(this);
		}
	}

	public final StandardOutTestContext standardOutTest() throws RecognitionException {
		StandardOutTestContext _localctx = new StandardOutTestContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_standardOutTest);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(126);
			match(T__14);
			setState(127);
			match(T__5);
			setState(128);
			match(T__15);
			setState(129);
			value();
			setState(134);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__8) {
				{
				{
				setState(130);
				match(T__8);
				setState(131);
				value();
				}
				}
				setState(136);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(137);
			match(T__16);
			setState(138);
			match(T__6);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static final String _serializedATN =
		"\u0004\u0001\u0015\u008d\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001"+
		"\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004"+
		"\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007"+
		"\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b"+
		"\u0002\f\u0007\f\u0002\r\u0007\r\u0001\u0000\u0001\u0000\u0001\u0000\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0004\u0002(\b\u0002\u000b\u0002\f\u0002)\u0001\u0002"+
		"\u0001\u0002\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003"+
		"\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0005\u0004"+
		"8\b\u0004\n\u0004\f\u0004;\t\u0004\u0003\u0004=\b\u0004\u0001\u0004\u0001"+
		"\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0003\u0004D\b\u0004\u0001"+
		"\u0005\u0001\u0005\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001"+
		"\u0006\u0001\u0006\u0001\u0006\u0003\u0006O\b\u0006\u0001\u0007\u0001"+
		"\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0003\u0007V\b\u0007\u0001"+
		"\b\u0001\b\u0001\b\u0001\b\u0005\b\\\b\b\n\b\f\b_\t\b\u0003\ba\b\b\u0001"+
		"\b\u0001\b\u0001\t\u0001\t\u0001\t\u0001\t\u0001\n\u0001\n\u0001\n\u0001"+
		"\n\u0005\nm\b\n\n\n\f\np\t\n\u0003\nr\b\n\u0001\n\u0001\n\u0001\u000b"+
		"\u0001\u000b\u0003\u000bx\b\u000b\u0001\f\u0001\f\u0001\f\u0001\f\u0001"+
		"\f\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0005\r\u0085\b\r\n"+
		"\r\f\r\u0088\t\r\u0001\r\u0001\r\u0001\r\u0001\r\u0000\u0000\u000e\u0000"+
		"\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012\u0014\u0016\u0018\u001a\u0000"+
		"\u0000\u008f\u0000\u001c\u0001\u0000\u0000\u0000\u0002\u001f\u0001\u0000"+
		"\u0000\u0000\u0004$\u0001\u0000\u0000\u0000\u0006-\u0001\u0000\u0000\u0000"+
		"\bC\u0001\u0000\u0000\u0000\nE\u0001\u0000\u0000\u0000\fN\u0001\u0000"+
		"\u0000\u0000\u000eU\u0001\u0000\u0000\u0000\u0010W\u0001\u0000\u0000\u0000"+
		"\u0012d\u0001\u0000\u0000\u0000\u0014h\u0001\u0000\u0000\u0000\u0016w"+
		"\u0001\u0000\u0000\u0000\u0018y\u0001\u0000\u0000\u0000\u001a~\u0001\u0000"+
		"\u0000\u0000\u001c\u001d\u0003\u0002\u0001\u0000\u001d\u001e\u0003\u0004"+
		"\u0002\u0000\u001e\u0001\u0001\u0000\u0000\u0000\u001f \u0005\u0001\u0000"+
		"\u0000 !\u0005\u0002\u0000\u0000!\"\u0003\u0006\u0003\u0000\"#\u0005\u0003"+
		"\u0000\u0000#\u0003\u0001\u0000\u0000\u0000$%\u0005\u0004\u0000\u0000"+
		"%\'\u0005\u0002\u0000\u0000&(\u0003\u0016\u000b\u0000\'&\u0001\u0000\u0000"+
		"\u0000()\u0001\u0000\u0000\u0000)\'\u0001\u0000\u0000\u0000)*\u0001\u0000"+
		"\u0000\u0000*+\u0001\u0000\u0000\u0000+,\u0005\u0003\u0000\u0000,\u0005"+
		"\u0001\u0000\u0000\u0000-.\u0005\u0005\u0000\u0000./\u0005\u0006\u0000"+
		"\u0000/0\u0003\f\u0006\u000001\u0005\u0007\u0000\u00001\u0007\u0001\u0000"+
		"\u0000\u000023\u0003\n\u0005\u00003<\u0005\b\u0000\u000049\u0003\f\u0006"+
		"\u000056\u0005\t\u0000\u000068\u0003\f\u0006\u000075\u0001\u0000\u0000"+
		"\u00008;\u0001\u0000\u0000\u000097\u0001\u0000\u0000\u00009:\u0001\u0000"+
		"\u0000\u0000:=\u0001\u0000\u0000\u0000;9\u0001\u0000\u0000\u0000<4\u0001"+
		"\u0000\u0000\u0000<=\u0001\u0000\u0000\u0000=>\u0001\u0000\u0000\u0000"+
		">?\u0005\n\u0000\u0000?D\u0001\u0000\u0000\u0000@A\u0003\n\u0005\u0000"+
		"AB\u0003\f\u0006\u0000BD\u0001\u0000\u0000\u0000C2\u0001\u0000\u0000\u0000"+
		"C@\u0001\u0000\u0000\u0000D\t\u0001\u0000\u0000\u0000EF\u0005\u0013\u0000"+
		"\u0000F\u000b\u0001\u0000\u0000\u0000GO\u0003\b\u0004\u0000HO\u0003\u000e"+
		"\u0007\u0000IO\u0003\n\u0005\u0000JK\u0005\b\u0000\u0000KL\u0003\f\u0006"+
		"\u0000LM\u0005\n\u0000\u0000MO\u0001\u0000\u0000\u0000NG\u0001\u0000\u0000"+
		"\u0000NH\u0001\u0000\u0000\u0000NI\u0001\u0000\u0000\u0000NJ\u0001\u0000"+
		"\u0000\u0000O\r\u0001\u0000\u0000\u0000PV\u0005\u0012\u0000\u0000QV\u0005"+
		"\u0013\u0000\u0000RV\u0005\u0014\u0000\u0000SV\u0003\u0010\b\u0000TV\u0003"+
		"\u0014\n\u0000UP\u0001\u0000\u0000\u0000UQ\u0001\u0000\u0000\u0000UR\u0001"+
		"\u0000\u0000\u0000US\u0001\u0000\u0000\u0000UT\u0001\u0000\u0000\u0000"+
		"V\u000f\u0001\u0000\u0000\u0000W`\u0005\u0002\u0000\u0000X]\u0003\u0012"+
		"\t\u0000YZ\u0005\t\u0000\u0000Z\\\u0003\u0012\t\u0000[Y\u0001\u0000\u0000"+
		"\u0000\\_\u0001\u0000\u0000\u0000][\u0001\u0000\u0000\u0000]^\u0001\u0000"+
		"\u0000\u0000^a\u0001\u0000\u0000\u0000_]\u0001\u0000\u0000\u0000`X\u0001"+
		"\u0000\u0000\u0000`a\u0001\u0000\u0000\u0000ab\u0001\u0000\u0000\u0000"+
		"bc\u0005\u0003\u0000\u0000c\u0011\u0001\u0000\u0000\u0000de\u0005\u0012"+
		"\u0000\u0000ef\u0005\u000b\u0000\u0000fg\u0003\u000e\u0007\u0000g\u0013"+
		"\u0001\u0000\u0000\u0000hq\u0005\f\u0000\u0000in\u0003\f\u0006\u0000j"+
		"k\u0005\t\u0000\u0000km\u0003\f\u0006\u0000lj\u0001\u0000\u0000\u0000"+
		"mp\u0001\u0000\u0000\u0000nl\u0001\u0000\u0000\u0000no\u0001\u0000\u0000"+
		"\u0000or\u0001\u0000\u0000\u0000pn\u0001\u0000\u0000\u0000qi\u0001\u0000"+
		"\u0000\u0000qr\u0001\u0000\u0000\u0000rs\u0001\u0000\u0000\u0000st\u0005"+
		"\n\u0000\u0000t\u0015\u0001\u0000\u0000\u0000ux\u0003\u0018\f\u0000vx"+
		"\u0003\u001a\r\u0000wu\u0001\u0000\u0000\u0000wv\u0001\u0000\u0000\u0000"+
		"x\u0017\u0001\u0000\u0000\u0000yz\u0005\r\u0000\u0000z{\u0005\u0006\u0000"+
		"\u0000{|\u0005\u000e\u0000\u0000|}\u0005\u0007\u0000\u0000}\u0019\u0001"+
		"\u0000\u0000\u0000~\u007f\u0005\u000f\u0000\u0000\u007f\u0080\u0005\u0006"+
		"\u0000\u0000\u0080\u0081\u0005\u0010\u0000\u0000\u0081\u0086\u0003\u000e"+
		"\u0007\u0000\u0082\u0083\u0005\t\u0000\u0000\u0083\u0085\u0003\u000e\u0007"+
		"\u0000\u0084\u0082\u0001\u0000\u0000\u0000\u0085\u0088\u0001\u0000\u0000"+
		"\u0000\u0086\u0084\u0001\u0000\u0000\u0000\u0086\u0087\u0001\u0000\u0000"+
		"\u0000\u0087\u0089\u0001\u0000\u0000\u0000\u0088\u0086\u0001\u0000\u0000"+
		"\u0000\u0089\u008a\u0005\u0011\u0000\u0000\u008a\u008b\u0005\u0007\u0000"+
		"\u0000\u008b\u001b\u0001\u0000\u0000\u0000\f)9<CNU]`nqw\u0086";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}