package maratmingazovr.ai.carsonella.world.generators

import androidx.compose.runtime.snapshots.SnapshotStateList
import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.ElementType.SubAtom
import maratmingazovr.ai.carsonella.chemistry.ElementType.Atom
import maratmingazovr.ai.carsonella.chemistry.ElementType.Molecule
import maratmingazovr.ai.carsonella.chemistry.ElementType.Star
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.Star
import maratmingazovr.ai.carsonella.chemistry.SubAtom
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.world.ReactionRequest
import maratmingazovr.ai.carsonella.world.currentTime
import kotlin.random.Random

class EntityGenerator(
    private val idGen: IdGenerator,
    private val entities: SnapshotStateList<Entity>, // текущий список атомов, который есть в мире
    private val pendingRequests: MutableList<ReactionRequest>, // буфер запросов реакций, дренится в фазе Resolve каждого tick'а
    private val logs: SnapshotStateList<String>,
    private val palette: SnapshotStateList<Element>,
    override val random: Random,
) : IEntityGenerator {

    override fun createEntity(
        species: Species,
        position: Position,
        direction: Vec2D,
        velocity: Float,
        energy: Float,
        environment: IEnvironment,
        electrons: Int,
    ): Entity = createEntityWithId(idGen.nextId(), species, position, direction, velocity, energy, environment, electrons)

    /**
     * То же, что createEntity, но с заранее заданным id вместо idGen.nextId().
     * Нужно при загрузке сохранения: id должны совпасть с сохранёнными, чтобы корректно
     * восстановить дерево среды (parentId ссылается на id родителя).
     */
    fun createEntityWithId(
        id: Long,
        species: Species,
        position: Position,
        direction: Vec2D,
        velocity: Float,
        energy: Float,
        environment: IEnvironment,
        electrons: Int,
    ): Entity {

        val entity = when (species) {
            is Species.Molecular -> Molecule(id = id, graph = species.graph, position = position, direction = direction, velocity = velocity, energy = energy, electrons = electrons)
            is Species.Elemental -> when (species.element.details.type) {
                SubAtom -> SubAtom(id = id, element = species.element, position = position, direction = direction, velocity = velocity, energy = energy, electrons = electrons)
                Atom -> Atom(id = id, element = species.element, position = position, direction = direction, velocity = velocity, energy = energy, electrons = electrons)
                Star -> Star(id = id, element = species.element, position = position, direction = direction, velocity = velocity, energy = energy, electrons = electrons)
                Molecule -> throw UnsupportedOperationException("Молекулы образуются реакциями как Species.Molecular(graph), не спавнятся по элементу")
            }
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
        return entity
    }

    // Перегрузка по Element — для загрузки сейвов (в них только Elemental) и прочих вызовов по элементу.
    fun createEntityWithId(
        id: Long,
        element: Element,
        position: Position,
        direction: Vec2D,
        velocity: Float,
        energy: Float,
        environment: IEnvironment,
        electrons: Int,
    ): Entity = createEntityWithId(id, Species.Elemental(element), position, direction, velocity, energy, environment, electrons)

}