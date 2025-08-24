package maratmingazovr.ai.carsonella.world.generators

import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.ISubAtomGenerator
import maratmingazovr.ai.carsonella.chemistry.sub_atoms.Electron
import maratmingazovr.ai.carsonella.chemistry.sub_atoms.Photon
import maratmingazovr.ai.carsonella.chemistry.sub_atoms.Proton
import maratmingazovr.ai.carsonella.chemistry.sub_atoms.SubAtom

class SubAtomGenerator(
    private val idGen: IdGenerator,
    private val entities: SnapshotStateList<Entity<*>>, // текущий список атомов, который есть в мире
    private val scope: CoroutineScope,
    private val environment: IEnvironment,
    private val logs: SnapshotStateList<String>,
) : ISubAtomGenerator {

    override fun createPhoton(
        position: Position,
        direction: Vec2D
    ): Entity<*> {
        val photon = Photon(id = idGen.nextId(), position = position, direction = direction)
        applyDefaultBehavior(photon)
        scope.launch { photon.init() }
        return photon
    }

    override fun createElectron(
        position: Position,
        direction: Vec2D
    ): Entity<*> {
        val electron = Electron(id = idGen.nextId(), position = position, direction = direction)
        applyDefaultBehavior(electron)
        scope.launch { electron.init() }
        return electron
    }

    override fun createProton(
        position: Position,
        direction: Vec2D
    ): Entity<*> {
        val proton = Proton(id = idGen.nextId(), position = position, direction = direction)
        applyDefaultBehavior(proton)
        scope.launch { proton.init() }
        return proton
    }


    private fun applyDefaultBehavior(subAtom: SubAtom<*>) {
        subAtom.apply {
            entities.add(this)
            setOnDeath { entities.remove(this)}
            setNeighbors { entities.toList().filter { it !== this }  }
            setEnvironment(environment)
            setLogger { log -> logs += "[${nowString()}] $log" }
        }
    }

    fun nowString(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val h = now.hour.toString().padStart(2, '0')
        val m = now.minute.toString().padStart(2, '0')
        val s = now.second.toString().padStart(2, '0')
        return "$h:$m:$s"
    }

}