package maratmingazovr.ai.carsonella.world.generators

import androidx.compose.runtime.snapshots.SnapshotStateList
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
import maratmingazovr.ai.carsonella.chemistry.ElementType.RecombinationModule
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.RecombinationModule
import maratmingazovr.ai.carsonella.chemistry.SpaceModule
import maratmingazovr.ai.carsonella.chemistry.Star
import maratmingazovr.ai.carsonella.chemistry.SubAtom
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.world.ReactionRequest
import maratmingazovr.ai.carsonella.world.currentTime
import kotlin.random.Random

class EntityGenerator(
    private val idGen: IdGenerator,
    private val entities: SnapshotStateList<Entity<*>>, // текущий список атомов, который есть в мире
    private val pendingRequests: MutableList<ReactionRequest>, // буфер запросов реакций, дренится в фазе Resolve каждого tick'а
    private val logs: SnapshotStateList<String>,
    private val palette: SnapshotStateList<Element>,
    override val random: Random,
) : IEntityGenerator {

    override fun createEntity(
        element: Element,
        position: Position,
        direction: Vec2D,
        velocity: Float,
        energy: Float,
        environment: IEnvironment,
        electrons: Int,
    ): Entity<*> {

        val entity = when(element.details.type) {
            SubAtom -> SubAtom(id = idGen.nextId(), element = element, position = position, direction = direction, velocity = velocity, energy = energy, electrons = electrons)
            Atom -> Atom(id = idGen.nextId(), element = element, position = position, direction = direction, velocity = velocity, energy = energy, electrons = electrons)
            Molecule -> Molecule(id = idGen.nextId(), element = element, position = position, direction = direction, velocity = velocity, energy = energy, electrons = electrons)
            Star -> Star(id = idGen.nextId(), element = element, position = position, direction = direction, velocity = velocity, energy = energy, electrons = electrons)
            SpaceModule -> SpaceModule(id = idGen.nextId(), element = element, position = position, direction = direction, velocity = velocity, energy = energy, electrons = electrons)
            RecombinationModule -> RecombinationModule(id = idGen.nextId(), element = element, position = position, direction = direction, velocity = velocity, energy = energy, electrons = electrons)
        }.apply {
            entities.add(this)
            setOnDeath {
                this.getEnvironment().removeEnvChild(this)
                entities.remove(this)
            }
            setEnvironment(environment)
            setNeighbors { getEnvironment().getEnvChildren().filter { it !== this }  } // простой вариант; для больших N потом сделаем spatial grid
            setRequestReaction {  reagents -> pendingRequests.add(ReactionRequest(reagents)) }
            setLogger { log -> logs += "${currentTime()}: $log" }
        }
        environment.addEnvChild(entity)
        //if(!palette.contains(entity.state().value.element)) palette.add(entity.state().value.element)
        return entity
    }

}