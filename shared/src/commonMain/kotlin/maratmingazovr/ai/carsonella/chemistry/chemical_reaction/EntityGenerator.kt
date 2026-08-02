package maratmingazovr.ai.carsonella.chemistry.chemical_reaction

import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.ElementType.SubAtom
import maratmingazovr.ai.carsonella.chemistry.ElementType.Atom
import maratmingazovr.ai.carsonella.chemistry.ElementType.Star
import maratmingazovr.ai.carsonella.chemistry.Kinematics
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.Star
import maratmingazovr.ai.carsonella.chemistry.SubAtom
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import kotlin.random.Random

class EntityGenerator(
    private val idGen: IdGenerator,
    private val entities: MutableList<Entity>, // текущий список атомов, который есть в мире
    private val pendingRequests: MutableList<ReactionRequest>, // буфер запросов реакций, дренится в фазе Resolve каждого tick'а
    private val log: (String) -> Unit, // куда писать лог; отметку времени ставит вызывающий (World)
    override val random: Random,
) : IEntityGenerator {

    override fun createEntity(
        element: Element, position: Position, direction: Vec2D,
        velocity: Float, energy: Float, environment: IEnvironment, electrons: Int,
    ): Entity = createEntityWithId(idGen.nextId(), element, position, direction, velocity, energy, environment, electrons)

    override fun createMolecule(
        graph: MoleculeGraph, position: Position, direction: Vec2D,
        velocity: Float, energy: Float, environment: IEnvironment, electrons: Int,
    ): Entity = register(
        Molecule(idGen.nextId(), graph, Kinematics(position, direction, velocity), energy, electrons),
        environment,
    )

    /**
     * То же, что createEntity, но с заранее заданным id вместо idGen.nextId().
     * Нужно при загрузке сохранения: id должны совпасть с сохранёнными, чтобы корректно
     * восстановить дерево среды (parentId ссылается на id родителя).
     *
     * Только по элементу: в сейвах молекулы не восстанавливаются (там формула, не граф).
     */
    fun createEntityWithId(
        id: Long,
        element: Element,
        position: Position,
        direction: Vec2D,
        velocity: Float,
        energy: Float,
        environment: IEnvironment,
        electrons: Int,
    ): Entity = register(
        // Единственное место, где тег ElementType ещё нужен: по нему выбирается класс сущности.
        when (element.details.type) {
            SubAtom -> SubAtom(id, element, position, direction, velocity, energy, electrons)
            Atom -> Atom(id, element, position, direction, velocity, energy, electrons)
            Star -> Star(id, element, position, direction, velocity, energy, electrons)
        },
        environment,
    )

    // Общая для обоих путей прописка новорождённой в мире: список, среда, соседи, канал реакций, лог.
    private fun register(entity: Entity, environment: IEnvironment): Entity {
        entity.apply {
            entities.add(this)
            setOnDeath {
                this.getEnvironment().removeEnvChild(this)
                entities.remove(this)
            }
            setEnvironment(environment)
            setNeighbors { getEnvironment().getEnvChildren().filter { it !== this } } // простой вариант; для больших N потом сделаем spatial grid
            setRequestReaction { reagents -> pendingRequests.add(ReactionRequest(reagents)) }
            setLogger(log)
        }
        environment.addEnvChild(entity)
        return entity
    }
}