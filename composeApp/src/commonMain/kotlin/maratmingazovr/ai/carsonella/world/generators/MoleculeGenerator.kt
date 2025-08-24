package maratmingazovr.ai.carsonella.world.generators

import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IMoleculeGenerator
import maratmingazovr.ai.carsonella.chemistry.molecules.Molecule
import maratmingazovr.ai.carsonella.world.ReactionRequest

class MoleculeGenerator(
    private val idGen: IdGenerator,
    private val entities: SnapshotStateList<Entity<*>>,
    private val scope: CoroutineScope,
    private val requestsChannel: Channel<ReactionRequest>,
) : IMoleculeGenerator {
    override fun createDiHydrogen(position: Position): Entity<*> {
        throw NotImplementedError()
//        val diHydrogen = DiHydrogen(id = idGen.nextId(), position = position)
//        applyDefaultBehavior(diHydrogen)
//        scope.launch { diHydrogen.init() }
//        return diHydrogen
    }

    private fun applyDefaultBehavior(molecule: Molecule<*>) {
        molecule.apply {
            entities.add(this)
            setOnDeath { entities.remove(this)}
            setNeighbors { entities.toList().filter { it !== this }  }
            setRequestReaction { reagents -> requestsChannel.trySend(ReactionRequest(reagents)) }
        }
    }
}