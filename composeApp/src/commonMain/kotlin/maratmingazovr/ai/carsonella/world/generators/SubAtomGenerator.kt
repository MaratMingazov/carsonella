package maratmingazovr.ai.carsonella.world.generators

import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.ISubAtomGenerator
import maratmingazovr.ai.carsonella.chemistry.SubAtom
import maratmingazovr.ai.carsonella.world.ReactionRequest
import maratmingazovr.ai.carsonella.world.nowString

class SubAtomGenerator(
    private val idGen: IdGenerator,
    private val entities: SnapshotStateList<Entity<*>>, // текущий список атомов, который есть в мире
    private val scope: CoroutineScope,
    private val requestsChannel: Channel<ReactionRequest>,
    private val environment: IEnvironment,
    private val logs: SnapshotStateList<String>,
) : ISubAtomGenerator {

    override fun createSubAtom(
        element: Element,
        position: Position,
        direction: Vec2D,
        velocity: Float,
        energy: Float,
    ): Entity<*> {
        val subAtom = SubAtom(id = idGen.nextId(), element = element, position = position, direction = direction, velocity = velocity, energy = energy)
        applyDefaultBehavior(subAtom)
        scope.launch { subAtom.init() }
        return subAtom
    }


    private fun applyDefaultBehavior(subAtom: Entity<*>) {
        subAtom.apply {
            entities.add(this)
            setOnDeath { entities.remove(this)}
            setNeighbors { entities.toList().filter { it !== this }  }
            setRequestReaction { reagents -> requestsChannel.trySend(ReactionRequest(reagents)) }
            setEnvironment(environment)
            setLogger { log -> logs += "${nowString()}: $log" }
        }
    }

}