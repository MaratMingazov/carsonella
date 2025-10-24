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
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.ElementType.SubAtom
import maratmingazovr.ai.carsonella.chemistry.ElementType.Atom
import maratmingazovr.ai.carsonella.chemistry.ElementType.Molecule
import maratmingazovr.ai.carsonella.chemistry.ElementType.Star
import maratmingazovr.ai.carsonella.chemistry.ElementType.SpaceModule
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.SpaceModule
import maratmingazovr.ai.carsonella.chemistry.Star
import maratmingazovr.ai.carsonella.chemistry.SubAtom
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.world.ReactionRequest
import maratmingazovr.ai.carsonella.world.currentTime

class EntityGenerator(
    private val idGen: IdGenerator,
    private val entities: SnapshotStateList<Entity<*>>, // текущий список атомов, который есть в мире
    private val scope: CoroutineScope,
    private val requestsChannel: Channel<ReactionRequest>, // это канал, в который атом может отправлять запросы на химическую реакцию
    private val logs: SnapshotStateList<String>,
    private val palette: SnapshotStateList<Element>,
    private val worldEnvironment: IEnvironment,
) : IEntityGenerator {

    override fun createEntity(
        element: Element,
        position: Position,
        direction: Vec2D,
        velocity: Float,
        energy: Float,
        environment: IEnvironment?,
    ): Entity<*> {

        val targetEnvironment = environment ?: worldEnvironment
        val entity = when(element.details.type) {
            SubAtom -> SubAtom(id = idGen.nextId(), element = element, position = position, direction = direction, velocity = velocity, energy = energy)
            Atom -> Atom(id = idGen.nextId(), element = element, position = position, direction = direction, velocity = velocity, energy = energy)
            Molecule -> Molecule(id = idGen.nextId(), element = element, position = position, direction = direction, velocity = velocity, energy = energy)
            Star -> Star(id = idGen.nextId(), element = element, position = position, direction = direction, velocity = velocity, energy = energy)
            SpaceModule -> SpaceModule(id = idGen.nextId(), element = element, position = position, direction = direction, velocity = velocity, energy = energy)
        }.apply {
            entities.add(this)
            setOnDeath {
                this.getEnvironment().removeEnvChild(this)
                entities.remove(this)
            }
            setEnvironment(targetEnvironment)
            setNeighbors { getEnvironment().getEnvChildren().filter { it !== this }  } // простой вариант; для больших N потом сделаем spatial grid
            setRequestReaction { reagents -> requestsChannel.trySend(ReactionRequest(reagents)) }
            setLogger { log -> logs += "${currentTime()}: $log" }
        }
        targetEnvironment.addEnvChild(entity)
        scope.launch { entity.init() }
        //if(!palette.contains(entity.state().value.element)) palette.add(entity.state().value.element)
        return entity
    }

}