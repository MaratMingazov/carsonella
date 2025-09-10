package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.H
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IMoleculeGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule

class HplusHtoH2(
    private val moleculeGenerator: IMoleculeGenerator,      // вот сюда нужно будет передать лямбду, с помощью которой можно создать молекулу водорода H2
) : ReactionRule {
    override val id = "H+H->H2"

    private var _hydrogen1 : Entity<*>? = null
    private var _hydrogen2 : Entity<*>? = null

    /**
     * Мы должны проверить, есть ли в реагентах два атома водорода, которые находятся на расстоянии ковалентной связи
     * При этом оба атома водорода должны быть живы.
     * Может оказаться несколько таких пар. Тогда берем ту пару, которая ближе друг к другу.
     * В результате вернем список из двух атомов водорода
     * Если таких кандидатов нет, то вернем пустой список
     */
    override suspend fun matches(reagents: List<Entity<*>>) : Boolean {
        _hydrogen1 = null
        _hydrogen2 = null
        val atoms = reagents
            .filter { it.state().value.element == H }
            .filter { it.state().value.alive }
        if (atoms.size < 2) return false

        val closestAtoms = findNearestToFirst(atoms)
        val firstHydrogen = closestAtoms.first
        val secondHydrogen = closestAtoms.second
        val distance = firstHydrogen.state().value.position.distanceTo(secondHydrogen.state().value.position)


        return if (distance < Element.H.radius * 2f) {
            _hydrogen1 = firstHydrogen
            _hydrogen2 = secondHydrogen
            true
        } else {
            false
        }
    }

    override suspend fun weight() = 0f

    override suspend fun produce(): ReactionOutcome {

        val (direction,velocity) = calculateHydrogenDirectionAndVelocity(_hydrogen1!!, _hydrogen2!!)
        return ReactionOutcome(
            consumed = listOf(_hydrogen1!!, _hydrogen2!!),
            spawn = listOf {
                moleculeGenerator.createMolecule(
                    Element.H2,
                    _hydrogen1!!.state().value.position,
                    direction,
                    velocity,
                    energy = 0f
                )
            },
        )
    }

    fun findNearestToFirst(atoms: List<Entity<*>>): Pair<Entity<*>, Entity<*>> {

        val first = atoms.first()
        val p0 = first.state().value.position

        val nearest = atoms
            .drop(1)
            .minBy { it.state().value.position.distanceSquareTo(p0) }

        return first to nearest
    }
}