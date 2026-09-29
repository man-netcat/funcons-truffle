package main

import cbs.CBSBaseVisitor
import cbs.CBSParser.*
import main.objects.*
import objects.DatatypeFunconObject
import objects.FunconObject
import org.antlr.v4.runtime.ParserRuleContext

/**
 * Reads one `.cbs` file and adds the objects it declares to the registry.
 *
 * @param registry every object in the corpus, which this file's objects are added
 * to and resolved against. A file does not own the corpus: a rule in one file may
 * name a funcon declared in another, and the order the files are read in is the
 * order the directory walk happens to produce.
 */
class ObjectCollector(private val registry: RegistryBuilder) : CBSBaseVisitor<Unit>() {

    /** The objects this file declared, under their own names and their aliases. */
    val objects: MutableSet<Object> = mutableSetOf()

    private lateinit var fileMetavariables: Set<Pair<ExprContext, ExprContext>>

    private fun addObjectReference(obj: Object) {
        objects.add(obj)
        registry.add(obj)
    }

    /**
     * Reads the metavariables, then the definitions.
     *
     * The metavariables have to be read first because a rule can use one that its
     * own file declares further down: CBS makes a metavariable visible to its whole
     * file, not only to what follows the block. Letting the visitor fill the map in
     * as it walked meant the order of the two decided which rules could see a
     * metavariable, which nothing in the types said.
     */
    fun collect(root: RootContext) {
        fileMetavariables = root.children
            .filterIsInstance<MetavariablesDefinitionContext>()
            .flatMap { ctx ->
                ctx.metavarDef().flatMap { def ->
                    def.variables.expr().mapNotNull { metaVar -> metaVar to def.supertype }
                }
            }
            .filter { (varName, _) -> varName is VariableContext }
            .toSet()

        visit(root)
    }

    override fun visitFunconDefinition(ctx: FunconDefinitionContext) {
        val dataContainer = FunconObject(
            ctx,
            rules = ctx.ruleDefinition(),
            term = ctx.rewritesTo,
            fileMetavariables,
            registry,
        )

        addObjectReference(dataContainer)
    }

    override fun visitDatatypeDefinition(ctx: DatatypeDefinitionContext) {
        val operator = ctx.op?.text ?: ""

        when (operator) {
            "<:" -> {
                val datatypeDataContainer = SupertypeDatatypeObject(ctx, fileMetavariables, registry)
                addObjectReference(datatypeDataContainer)

            }

            "::=" -> {
                val definitions = extractAndOrExprs(ctx.definition)
                val datatypeDataContainer = AlgebraicDatatypeObject(ctx, fileMetavariables, registry)
                addObjectReference(datatypeDataContainer)


                definitions.filter { def -> def is FunconExpressionContext }
                    .map { funcon ->
                        funcon as FunconExpressionContext
                        val dataContainer =
                            DatatypeFunconObject(
                                funcon,
                                datatypeDataContainer,
                                fileMetavariables,
                                registry,
                            )
                        addObjectReference(dataContainer)
                        datatypeDataContainer.definitions.add(dataContainer)
                    }
            }
        }
    }

    override fun visitTypeDefinition(ctx: TypeDefinitionContext) {
        val dataContainer = TypeObject(ctx, fileMetavariables, registry)
        addObjectReference(dataContainer)
    }

    /**
     * Every kind of entity definition produces the same object.
     *
     * The four rules differ only in their keyword and, for a control entity, in a
     * polarity the generator does not act on: an entity is read and written by name
     * (`getEntity` and `putEntity`), so `contextual`, `mutable`, `control?` and
     * `control!` are one case here rather than four.
     */
    private fun processEntityDefinition(ctx: ParserRuleContext) {
        addObjectReference(EntityObject(ctx, fileMetavariables, registry))
    }

    override fun visitControlEntityDefinition(ctx: ControlEntityDefinitionContext) =
        processEntityDefinition(ctx)

    override fun visitMutableEntityDefinition(ctx: MutableEntityDefinitionContext) =
        processEntityDefinition(ctx)

    override fun visitContextualEntityDefinition(ctx: ContextualEntityDefinitionContext) =
        processEntityDefinition(ctx)
}
