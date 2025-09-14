package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.H
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule

class AtomPlusAtomToMolecule(
    private val entityGenerator: IEntityGenerator,      // вот сюда нужно будет передать лямбду, с помощью которой можно создать молекулу водорода H2
    private val element1: Element,
    private val element2: Element,
    private val resultElement: Element,
) : ReactionRule {
    override val id = "Atom + Atom -> Molecule"

    private var atom1 : Entity<*>? = null
    private var atom2 : Entity<*>? = null

    override suspend fun matches(reagents: List<Entity<*>>) : Boolean {
        atom1 = null
        atom2 = null
        if (reagents.size < 2) return false
        val firstAtom = reagents.first()
        val firstAtomPosition = reagents.first().state().value.position
        if (firstAtom.state().value.element != element1) return false

        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filter { it.state().value.element == element2 }
            .filter { it.state().value.alive }
            .map { it to  it.state().value.position.distanceSquareTo(firstAtomPosition)}
            .minByOrNull { it.second }
            ?: return false


        return if (distanceSquare < element1.radius * element2.radius * 2f) {
            atom1 = firstAtom
            atom2 = secondAtom
            true
        } else {
            false
        }
    }

    override suspend fun weight() = 0f

    override suspend fun produce(): ReactionOutcome {

        val (direction,velocity) = calculateHydrogenDirectionAndVelocity(atom1!!, atom2!!)
        return ReactionOutcome(
            consumed = listOf(atom1!!, atom2!!),
            spawn = listOf {
                entityGenerator.createEntity(
                    resultElement,
                    atom1!!.state().value.position,
                    direction,
                    velocity,
                    energy = atom1!!.state().value.energy + atom2!!.state().value.energy
                )
            },
        )
    }
}