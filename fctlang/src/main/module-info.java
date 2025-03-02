module fctlang {
    requires kotlin.stdlib;
    requires org.antlr.antlr4.runtime;
    requires org.graalvm.truffle;

    provides com.oracle.truffle.api.TruffleLanguage
            with language.FCTLanguage;
}