package maratmingazovr.ai.carsonella.chemistry.chemical_reaction

import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.registry.AtomElement
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.ElementType.SubAtom
import maratmingazovr.ai.carsonella.chemistry.ElementType.Atom
import maratmingazovr.ai.carsonella.chemistry.ElementType.Star
import maratmingazovr.ai.carsonella.chemistry.HydrogenBond
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.MoleculeAtom
import maratmingazovr.ai.carsonella.chemistry.Star
import maratmingazovr.ai.carsonella.chemistry.SubAtom
import maratmingazovr.ai.carsonella.chemistry.MoleculeShape
import kotlin.random.Random

class EntityGenerator(
    private val idGen: IdGenerator,
    private val entities: MutableList<Entity>, // текущий список атомов, который есть в мире
    private val pendingRequests: MutableList<ReactionRequest>, // буфер запросов реакций, дренится в фазе Resolve каждого tick'а
    private val log: (String) -> Unit, // куда писать лог; отметку времени ставит вызывающий (World)
    override val random: Random,
) : IEntityGenerator {

    override fun createAtom(
        element: AtomElement, position: Position, direction: Vec2D,
        velocity: Float, energy: Float, environment: IEnvironment, electrons: Int,
    ): Entity = createAtomWithId(idGen.nextId(), element, position, direction, velocity, energy, environment, electrons)

    override fun createMolecule(
        shape: MoleculeShape, energy: Float, environment: IEnvironment, electrons: Int,
    ): Entity = register(
        Molecule(idGen.nextId(), shape, energy, electrons),
        environment,
    )

    override fun createMolecule(
        atom1: Atom,
        atom2: Atom,
        environment: IEnvironment,
    ): Entity = register(
        Molecule(idGen.nextId(), atom1, atom2),
        environment,
    )

    override fun createMolecule(
        molecule1: Molecule,
        atom1: MoleculeAtom,
        molecule2: Molecule,
        atom2: MoleculeAtom,
        environment: IEnvironment,
    ): Entity = register(
        Molecule(idGen.nextId(), molecule1, atom1, molecule2, atom2),
        environment,
    )

    override fun createMolecule(
        molecule: Molecule,
        atom: MoleculeAtom,
        partner: Atom,
        environment: IEnvironment,
    ): Entity = register(
        Molecule(idGen.nextId(), molecule, atom, partner),
        environment,
    )

    override fun createHydrogenBond(
        molecule1: Molecule,
        atom1: MoleculeAtom,
        molecule2: Molecule,
        atom2: MoleculeAtom,
        environment: IEnvironment,
    ): Entity = register(
        HydrogenBond(idGen.nextId(), energy = 0f, molecule1 = molecule1, localId1 = atom1.localId, molecule2 = molecule2, localId2 = atom2.localId),
        environment,
    )

    fun createAtomWithId(
        id: Long,
        element: AtomElement,
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
            Star -> Star(id, element, position, direction, velocity, electrons)
        },
        environment,
    )


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
    } // После создания атома/молекулы нужно прописать им доп свойства
}