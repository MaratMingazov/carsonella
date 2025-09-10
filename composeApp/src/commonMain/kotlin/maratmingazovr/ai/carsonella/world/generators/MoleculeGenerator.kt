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
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IMoleculeGenerator
import maratmingazovr.ai.carsonella.chemistry.molecules.Molecule
import maratmingazovr.ai.carsonella.world.ReactionRequest
import maratmingazovr.ai.carsonella.world.nowString

class MoleculeGenerator(
    private val idGen: IdGenerator,
    private val entities: SnapshotStateList<Entity<*>>,
    private val scope: CoroutineScope,
    private val requestsChannel: Channel<ReactionRequest>,
    private val environment: IEnvironment,
    private val logs: SnapshotStateList<String>,
    private val palette: SnapshotStateList<Element>,
) : IMoleculeGenerator {
    override fun createMolecule(
        element: Element,
        position: Position,
        direction: Vec2D,
        velocity: Float,
    ): Entity<*> {
        val diHydrogen = Molecule(id = idGen.nextId(), element = element, position = position, direction = direction, velocity = velocity)
        applyDefaultBehavior(diHydrogen)
        scope.launch { diHydrogen.init() }
        if(!palette.contains(diHydrogen.state().value.element)) palette.add(diHydrogen.state().value.element)
        return diHydrogen
    }

    private fun applyDefaultBehavior(molecule: Entity<*>) {
        molecule.apply {
            entities.add(this)
            setOnDeath { entities.remove(this)}
            setNeighbors { entities.toList().filter { it !== this }  }
            setRequestReaction { reagents -> requestsChannel.trySend(ReactionRequest(reagents)) }
            setEnvironment(environment)
            setLogger { log -> logs += "${nowString()}: $log" }
        }
    }
}