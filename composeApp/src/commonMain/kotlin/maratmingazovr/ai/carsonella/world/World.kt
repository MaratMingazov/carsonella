package maratmingazovr.ai.carsonella.world

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.ChemicalReactionResolver
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.HplusHtoH2
import maratmingazovr.ai.carsonella.world.generators.AtomGenerator
import maratmingazovr.ai.carsonella.world.generators.IdGenerator
import maratmingazovr.ai.carsonella.world.generators.MoleculeGenerator
import maratmingazovr.ai.carsonella.world.generators.SubAtomGenerator

class World(
    private val _scope: CoroutineScope,
) {

    private val _idGen: IdGenerator = IdGenerator()
    private val _requestsChannel =  Channel<ReactionRequest>(capacity = Channel.UNLIMITED)
    val environment = Environment(
        0f,
        0f,
        0.00000000000000000000000001f
    )
    val entities =  mutableStateListOf<Entity<*>>()
    val logs =  mutableStateListOf<String>()
    val subAtomGenerator = SubAtomGenerator(_idGen, entities, _scope, environment, logs)
    val atomGenerator = AtomGenerator(_idGen, entities, _scope, _requestsChannel, environment, logs)
    val moleculeGenerator = MoleculeGenerator(_idGen, entities, _scope, _requestsChannel)
    private val _worldMutex = Mutex()

    private val _chemicalReactionResolver = ChemicalReactionResolver(
        rules = listOf(HplusHtoH2(moleculeGenerator))
    )

    fun start() {
        _scope.launch {
            for (reactinoRequest in _requestsChannel) {
                _worldMutex.withLock { runReaction(reactinoRequest) }
            }
        }
    }



    suspend fun runReaction(reactionRequest: ReactionRequest) {
        println("Пришел запрос на реакцию. Реагенты: ${reactionRequest.reagents.size}")
        val result = _chemicalReactionResolver.resolve(reactionRequest.reagents) ?: return
        println("Результат реакции:")
//        val consumedElements = result.consumed.joinToString(", ") {
//            it.state().value.label() + "-" + it.state().value.id()
//        }
//        println("  Реагенты: $consumedElements")

        for (e in result.consumed) {
            e.destroy()
        }

        result.spawn.forEach { it() }
    }
}



data class ReactionRequest(val reagents: List<Entity<*>>)


