// Generated from FCT.g4 by ANTLR 4.13.2
package trufflegen.antlr4;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class FCTLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, T__11=12, T__12=13, T__13=14, T__14=15, T__15=16, T__16=17, 
		STRING=18, IDENTIFIER=19, NUMBER=20, WS=21;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"T__0", "T__1", "T__2", "T__3", "T__4", "T__5", "T__6", "T__7", "T__8", 
			"T__9", "T__10", "T__11", "T__12", "T__13", "T__14", "T__15", "T__16", 
			"STRING", "IDENTIFIER", "NUMBER", "WS"
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


	public FCTLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "FCT.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getChannelNames() { return channelNames; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	public static final String _serializedATN =
		"\u0004\u0000\u0015\u00a2\u0006\uffff\uffff\u0002\u0000\u0007\u0000\u0002"+
		"\u0001\u0007\u0001\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002"+
		"\u0004\u0007\u0004\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002"+
		"\u0007\u0007\u0007\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002"+
		"\u000b\u0007\u000b\u0002\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e"+
		"\u0002\u000f\u0007\u000f\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011"+
		"\u0002\u0012\u0007\u0012\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014"+
		"\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0002\u0001\u0002"+
		"\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003"+
		"\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004"+
		"\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004"+
		"\u0001\u0005\u0001\u0005\u0001\u0006\u0001\u0006\u0001\u0007\u0001\u0007"+
		"\u0001\b\u0001\b\u0001\t\u0001\t\u0001\n\u0001\n\u0001\n\u0001\n\u0001"+
		"\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001"+
		"\u000b\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f"+
		"\u0001\f\u0001\f\u0001\f\u0001\f\u0001\r\u0001\r\u0001\r\u0001\r\u0001"+
		"\r\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0001\u000e\u0001\u000e"+
		"\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e"+
		"\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000f"+
		"\u0001\u000f\u0001\u0010\u0001\u0010\u0001\u0011\u0001\u0011\u0005\u0011"+
		"\u0089\b\u0011\n\u0011\f\u0011\u008c\t\u0011\u0001\u0011\u0001\u0011\u0001"+
		"\u0012\u0001\u0012\u0005\u0012\u0092\b\u0012\n\u0012\f\u0012\u0095\t\u0012"+
		"\u0001\u0013\u0004\u0013\u0098\b\u0013\u000b\u0013\f\u0013\u0099\u0001"+
		"\u0014\u0004\u0014\u009d\b\u0014\u000b\u0014\f\u0014\u009e\u0001\u0014"+
		"\u0001\u0014\u0001\u008a\u0000\u0015\u0001\u0001\u0003\u0002\u0005\u0003"+
		"\u0007\u0004\t\u0005\u000b\u0006\r\u0007\u000f\b\u0011\t\u0013\n\u0015"+
		"\u000b\u0017\f\u0019\r\u001b\u000e\u001d\u000f\u001f\u0010!\u0011#\u0012"+
		"%\u0013\'\u0014)\u0015\u0001\u0000\u0004\u0003\u0000AZ__az\u0004\u0000"+
		"09AZ__az\u0001\u000009\u0003\u0000\t\n\r\r  \u00a5\u0000\u0001\u0001\u0000"+
		"\u0000\u0000\u0000\u0003\u0001\u0000\u0000\u0000\u0000\u0005\u0001\u0000"+
		"\u0000\u0000\u0000\u0007\u0001\u0000\u0000\u0000\u0000\t\u0001\u0000\u0000"+
		"\u0000\u0000\u000b\u0001\u0000\u0000\u0000\u0000\r\u0001\u0000\u0000\u0000"+
		"\u0000\u000f\u0001\u0000\u0000\u0000\u0000\u0011\u0001\u0000\u0000\u0000"+
		"\u0000\u0013\u0001\u0000\u0000\u0000\u0000\u0015\u0001\u0000\u0000\u0000"+
		"\u0000\u0017\u0001\u0000\u0000\u0000\u0000\u0019\u0001\u0000\u0000\u0000"+
		"\u0000\u001b\u0001\u0000\u0000\u0000\u0000\u001d\u0001\u0000\u0000\u0000"+
		"\u0000\u001f\u0001\u0000\u0000\u0000\u0000!\u0001\u0000\u0000\u0000\u0000"+
		"#\u0001\u0000\u0000\u0000\u0000%\u0001\u0000\u0000\u0000\u0000\'\u0001"+
		"\u0000\u0000\u0000\u0000)\u0001\u0000\u0000\u0000\u0001+\u0001\u0000\u0000"+
		"\u0000\u00033\u0001\u0000\u0000\u0000\u00055\u0001\u0000\u0000\u0000\u0007"+
		"7\u0001\u0000\u0000\u0000\t=\u0001\u0000\u0000\u0000\u000bI\u0001\u0000"+
		"\u0000\u0000\rK\u0001\u0000\u0000\u0000\u000fM\u0001\u0000\u0000\u0000"+
		"\u0011O\u0001\u0000\u0000\u0000\u0013Q\u0001\u0000\u0000\u0000\u0015S"+
		"\u0001\u0000\u0000\u0000\u0017W\u0001\u0000\u0000\u0000\u0019^\u0001\u0000"+
		"\u0000\u0000\u001bj\u0001\u0000\u0000\u0000\u001du\u0001\u0000\u0000\u0000"+
		"\u001f\u0082\u0001\u0000\u0000\u0000!\u0084\u0001\u0000\u0000\u0000#\u0086"+
		"\u0001\u0000\u0000\u0000%\u008f\u0001\u0000\u0000\u0000\'\u0097\u0001"+
		"\u0000\u0000\u0000)\u009c\u0001\u0000\u0000\u0000+,\u0005g\u0000\u0000"+
		",-\u0005e\u0000\u0000-.\u0005n\u0000\u0000./\u0005e\u0000\u0000/0\u0005"+
		"r\u0000\u000001\u0005a\u0000\u000012\u0005l\u0000\u00002\u0002\u0001\u0000"+
		"\u0000\u000034\u0005{\u0000\u00004\u0004\u0001\u0000\u0000\u000056\u0005"+
		"}\u0000\u00006\u0006\u0001\u0000\u0000\u000078\u0005t\u0000\u000089\u0005"+
		"e\u0000\u00009:\u0005s\u0000\u0000:;\u0005t\u0000\u0000;<\u0005s\u0000"+
		"\u0000<\b\u0001\u0000\u0000\u0000=>\u0005f\u0000\u0000>?\u0005u\u0000"+
		"\u0000?@\u0005n\u0000\u0000@A\u0005c\u0000\u0000AB\u0005o\u0000\u0000"+
		"BC\u0005n\u0000\u0000CD\u0005-\u0000\u0000DE\u0005t\u0000\u0000EF\u0005"+
		"e\u0000\u0000FG\u0005r\u0000\u0000GH\u0005m\u0000\u0000H\n\u0001\u0000"+
		"\u0000\u0000IJ\u0005:\u0000\u0000J\f\u0001\u0000\u0000\u0000KL\u0005;"+
		"\u0000\u0000L\u000e\u0001\u0000\u0000\u0000MN\u0005(\u0000\u0000N\u0010"+
		"\u0001\u0000\u0000\u0000OP\u0005,\u0000\u0000P\u0012\u0001\u0000\u0000"+
		"\u0000QR\u0005)\u0000\u0000R\u0014\u0001\u0000\u0000\u0000ST\u0005|\u0000"+
		"\u0000TU\u0005-\u0000\u0000UV\u0005>\u0000\u0000V\u0016\u0001\u0000\u0000"+
		"\u0000WX\u0005t\u0000\u0000XY\u0005u\u0000\u0000YZ\u0005p\u0000\u0000"+
		"Z[\u0005l\u0000\u0000[\\\u0005e\u0000\u0000\\]\u0005(\u0000\u0000]\u0018"+
		"\u0001\u0000\u0000\u0000^_\u0005r\u0000\u0000_`\u0005e\u0000\u0000`a\u0005"+
		"s\u0000\u0000ab\u0005u\u0000\u0000bc\u0005l\u0000\u0000cd\u0005t\u0000"+
		"\u0000de\u0005-\u0000\u0000ef\u0005t\u0000\u0000fg\u0005e\u0000\u0000"+
		"gh\u0005r\u0000\u0000hi\u0005m\u0000\u0000i\u001a\u0001\u0000\u0000\u0000"+
		"jk\u0005n\u0000\u0000kl\u0005u\u0000\u0000lm\u0005l\u0000\u0000mn\u0005"+
		"l\u0000\u0000no\u0005-\u0000\u0000op\u0005v\u0000\u0000pq\u0005a\u0000"+
		"\u0000qr\u0005l\u0000\u0000rs\u0005u\u0000\u0000st\u0005e\u0000\u0000"+
		"t\u001c\u0001\u0000\u0000\u0000uv\u0005s\u0000\u0000vw\u0005t\u0000\u0000"+
		"wx\u0005a\u0000\u0000xy\u0005n\u0000\u0000yz\u0005d\u0000\u0000z{\u0005"+
		"a\u0000\u0000{|\u0005r\u0000\u0000|}\u0005d\u0000\u0000}~\u0005-\u0000"+
		"\u0000~\u007f\u0005o\u0000\u0000\u007f\u0080\u0005u\u0000\u0000\u0080"+
		"\u0081\u0005t\u0000\u0000\u0081\u001e\u0001\u0000\u0000\u0000\u0082\u0083"+
		"\u0005[\u0000\u0000\u0083 \u0001\u0000\u0000\u0000\u0084\u0085\u0005]"+
		"\u0000\u0000\u0085\"\u0001\u0000\u0000\u0000\u0086\u008a\u0005\"\u0000"+
		"\u0000\u0087\u0089\t\u0000\u0000\u0000\u0088\u0087\u0001\u0000\u0000\u0000"+
		"\u0089\u008c\u0001\u0000\u0000\u0000\u008a\u008b\u0001\u0000\u0000\u0000"+
		"\u008a\u0088\u0001\u0000\u0000\u0000\u008b\u008d\u0001\u0000\u0000\u0000"+
		"\u008c\u008a\u0001\u0000\u0000\u0000\u008d\u008e\u0005\"\u0000\u0000\u008e"+
		"$\u0001\u0000\u0000\u0000\u008f\u0093\u0007\u0000\u0000\u0000\u0090\u0092"+
		"\u0007\u0001\u0000\u0000\u0091\u0090\u0001\u0000\u0000\u0000\u0092\u0095"+
		"\u0001\u0000\u0000\u0000\u0093\u0091\u0001\u0000\u0000\u0000\u0093\u0094"+
		"\u0001\u0000\u0000\u0000\u0094&\u0001\u0000\u0000\u0000\u0095\u0093\u0001"+
		"\u0000\u0000\u0000\u0096\u0098\u0007\u0002\u0000\u0000\u0097\u0096\u0001"+
		"\u0000\u0000\u0000\u0098\u0099\u0001\u0000\u0000\u0000\u0099\u0097\u0001"+
		"\u0000\u0000\u0000\u0099\u009a\u0001\u0000\u0000\u0000\u009a(\u0001\u0000"+
		"\u0000\u0000\u009b\u009d\u0007\u0003\u0000\u0000\u009c\u009b\u0001\u0000"+
		"\u0000\u0000\u009d\u009e\u0001\u0000\u0000\u0000\u009e\u009c\u0001\u0000"+
		"\u0000\u0000\u009e\u009f\u0001\u0000\u0000\u0000\u009f\u00a0\u0001\u0000"+
		"\u0000\u0000\u00a0\u00a1\u0006\u0014\u0000\u0000\u00a1*\u0001\u0000\u0000"+
		"\u0000\u0005\u0000\u008a\u0093\u0099\u009e\u0001\u0006\u0000\u0000";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}