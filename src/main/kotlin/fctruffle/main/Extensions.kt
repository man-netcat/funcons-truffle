package fctruffle.main

//import fctruffle.generated.IntegersNode
//import fctruffle.generated.SetsNode
//import fctruffle.generated.StringsNode
//
//
//open class FCTIntegerNode(val value: Int) : IntegersNode()
//
//fun Int.toIntegersNode(): IntegersNode {
//    return FCTIntegerNode(this)
//}
//
//open class FCTSetNode<T>(val value: Set<T>) : SetsNode<T>()
//
//fun <T>Set<T>.toSetNode(): SetsNode<T> {
//    return FCTSetNode<T>(this)
//}
//
//open class FCTStringNode(override val value: String) : StringsNode()
//
//fun String.toStringsNode(): StringsNode {
//    return FCTStringNode(this)
//}