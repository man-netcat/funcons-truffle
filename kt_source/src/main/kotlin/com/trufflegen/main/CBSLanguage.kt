import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.source.Source
import com.trufflegen.main.FCTParser

@TruffleLanguage.Registration(
    id = CBSLanguage.ID,
    name = "cbslang",
    defaultMimeType = CBSLanguage.MIME_TYPE,
    characterMimeTypes = [CBSLanguage.MIME_TYPE]
)
class CBSLanguage : TruffleLanguage<Nothing>() {
    companion object {
        const val ID: String = "cbslang"
        const val MIME_TYPE: String = "application/x-cbslang"
    }

    override fun createContext(env: Env?): Nothing {
        TODO("Not yet implemented")
    }

    override fun isObjectOfLanguage(`object`: Any?): Boolean {
        TODO("Not yet implemented")
    }

    fun parse(source: Source, vararg argumentNames: String): CallTarget {
        val code: String = source.characters.toString()
        val root: RootNode = FCTParser(code).parseFile()
        return Truffle.getRuntime().createCallTarget(root)
    }
}
