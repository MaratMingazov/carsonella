package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule
import maratmingazovr.ai.carsonella.randomDirection

/**
 * Фотодиссоциация (светом)
 *
 * Если в молекулу H₂ попадает ультрафиолетовый или рентгеновский фотон с энергией ≥ 4.5 эВ,
 * то молекула поглощает его и разрывается:
 * 	•	H₂ + hν → H + H
 * 	•	В астрономии это важный процесс — ультрафиолет от звёзд разрушает межзвёздные облака водорода.
 */
class H2plusPhotonToHandH(
    private val entityGenerator: IEntityGenerator,
) : ReactionRule {
    override val id = "H₂ + hν → H + H"

    private var diHydrogen : Entity<*>? = null
    private var photon : Entity<*>? = null


    override suspend fun matches(reagents: List<Entity<*>>) : Boolean {
        diHydrogen = null
        photon = null

        if (reagents.size < 2) return false

        val first = reagents.first()
        if (first.state().value.element != Element.H2) return false
        if (!first.state().value.alive) return false
        val others = reagents.drop(1)
        val activationDistanceSquare = Element.H2.radius * Element.H2.radius

        val (nearestPhoton, distance) = others
            .asSequence()
            .filter { it.state().value.element == Element.Photon }
            .filter { it.state().value.alive }
            .map { it to first.state().value.position.distanceSquareTo(it.state().value.position) }
            .minByOrNull { it.second }
            ?: return false

        if (distance <= activationDistanceSquare) {
            diHydrogen = first
            photon = nearestPhoton
            return true
        }
        return false
    }

    override suspend fun weight() = 0f

    override suspend fun produce(): ReactionOutcome {

        val H_ev = 4.5f // энергия связи молекулы водорода
        if (diHydrogen!!.state().value.energy + photon!!.state().value.energy < H_ev) {
            // энергии фотона не хватает, чтобы разбить молекулу водорода, поэтому мы поглощаем энергию
            return ReactionOutcome(
                consumed = listOf(photon!!),
                updateState = listOf { diHydrogen!!.addEnergy(photon!!.state().value.energy) },
            )
        } else {
            // фотон разбивает молекулу водорода, образуются два атома водорода.
            val freeEnergy = diHydrogen!!.state().value.energy + photon!!.state().value.energy - H_ev
            val firstHydrogenDirection = randomDirection()
            val secondHydrogenDirection = Vec2D(-1 * firstHydrogenDirection.x, -1 * firstHydrogenDirection.y)
            return ReactionOutcome(
                consumed = listOf(photon!!, diHydrogen!!),
                spawn = listOf {
                    entityGenerator.createEntity(
                        Element.H,
                        diHydrogen!!.state().value.position.plus(Position(-1.5f*Element.H.radius, 0f)),
                        firstHydrogenDirection,
                        diHydrogen!!.state().value.velocity,
                        energy = 0f
                    )
                    entityGenerator.createEntity(
                        Element.H,
                        diHydrogen!!.state().value.position.plus(Position(1.5f*Element.H.radius, 0f)),
                        secondHydrogenDirection,
                        freeEnergy,
                        energy = 0f
                    )
                },
            )
        }

    }


}