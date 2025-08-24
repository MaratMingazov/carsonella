package maratmingazovr.ai.carsonella.world.generators

import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.atoms.Atom
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IAtomGenerator
import maratmingazovr.ai.carsonella.world.ReactionRequest

class AtomGenerator(
    private val idGen: IdGenerator,
    private val entities: SnapshotStateList<Entity<*>>, // текущий список атомов, который есть в мире
    private val scope: CoroutineScope,
    private val requestsChannel: Channel<ReactionRequest>, // это канал, в который атом может отправлять запросы на химическую реакцию
    private val environment: IEnvironment,
    private val logs: SnapshotStateList<String>,
) : IAtomGenerator {

    override fun createHydrogen(
        position: Position,
        velocity: Vec2D,
    ): Entity<*> {
        throw NotImplementedError()
//        val hydrogen = Hydrogen(id = idGen.nextId(), position = position, velocity = velocity)
//        applyDefaultBehavior(hydrogen)
//        scope.launch { hydrogen.init() }
//        return hydrogen
    }

//    override fun createOxygen(
//        position: Position,
//    ): Entity<*> {
//        val oxygen = Oxygen(id = idGen.nextId(), position = position)
//        applyDefaultBehavior(oxygen)
//        scope.launch { oxygen.init() }
//        return oxygen
//    }

    private fun applyDefaultBehavior(atom: Atom<*>) {
        atom.apply {
            entities.add(this)
            setOnDeath { entities.remove(this)}
            setNeighbors { entities.toList().filter { it !== this }  } // простой вариант; для больших N потом сделаем spatial grid
            setRequestReaction { reagents -> requestsChannel.trySend(ReactionRequest(reagents)) }
            setEnvironment(environment)
            setLogger { log ->  logs += log }
        }
    }
}