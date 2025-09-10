package maratmingazovr.ai.carsonella.world.generators

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.packInts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.atoms.Atom
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IAtomGenerator
import maratmingazovr.ai.carsonella.world.ReactionRequest
import maratmingazovr.ai.carsonella.world.nowString

class AtomGenerator(
    private val idGen: IdGenerator,
    private val entities: SnapshotStateList<Entity<*>>, // текущий список атомов, который есть в мире
    private val scope: CoroutineScope,
    private val requestsChannel: Channel<ReactionRequest>, // это канал, в который атом может отправлять запросы на химическую реакцию
    private val environment: IEnvironment,
    private val logs: SnapshotStateList<String>,
    private val palette: SnapshotStateList<Element>,
) : IAtomGenerator {

    override fun createAtom(
        element: Element,
        position: Position,
        direction: Vec2D,
        velocity: Float,
    ): Entity<*> {
        val hydrogen = Atom(id = idGen.nextId(), element = element, position = position, direction = direction, velocity = velocity)
        applyDefaultBehavior(hydrogen)
        scope.launch { hydrogen.init() }
        if(!palette.contains(hydrogen.state().value.element)) palette.add(hydrogen.state().value.element)
        return hydrogen
    }

    private fun applyDefaultBehavior(atom: Entity<*>) {
        atom.apply {
            entities.add(this)
            setOnDeath { entities.remove(this)}
            setNeighbors { entities.toList().filter { it !== this }  } // простой вариант; для больших N потом сделаем spatial grid
            setRequestReaction { reagents -> requestsChannel.trySend(ReactionRequest(reagents)) }
            setEnvironment(environment)
            setLogger { log -> logs += "${nowString()}: $log" }
        }
    }
}