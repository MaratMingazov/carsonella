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
 * Фотодиссоциация или Фотоэффект
 * Когда молекула достигает энергетического порога, она распадается.
 */
class MoleculePlusPhotonToAtomAndAtom(
    private val entityGenerator: IEntityGenerator,
    private val moleculeElement: Element,
    private val resultElement1: Element,
    private val resultElement2: Element,
) : ReactionRule {
    override val id = "H₂ + hν → H + H"

    private var molecule : Entity<*>? = null
    private var photon : Entity<*>? = null


    override suspend fun matches(reagents: List<Entity<*>>) : Boolean {
        molecule = null
        photon = null

        if (reagents.size < 2) return false

        val first = reagents.first()
        if (first.state().value.element.energyThreshold == null) return false
        if (first.state().value.element != moleculeElement) return false
        if (!first.state().value.alive) return false
        val others = reagents.drop(1)
        val activationDistanceSquare = moleculeElement.radius * moleculeElement.radius

        val (nearestPhoton, distance) = others
            .asSequence()
            .filter { it.state().value.element == Element.Photon }
            .filter { it.state().value.alive }
            .map { it to first.state().value.position.distanceSquareTo(it.state().value.position) }
            .minByOrNull { it.second }
            ?: return false

        if (distance <= activationDistanceSquare) {
            molecule = first
            photon = nearestPhoton
            return true
        }
        return false
    }

    override suspend fun weight() = 0f

    override suspend fun produce(): ReactionOutcome {

        /**
         *  Энергетический порог молекулы.
         *  Если в молекулу прилетел фотон, то молекула либо заберет эту энергию, либо сама распадется
         */
        val energyThreshold = moleculeElement.energyThreshold!!

        if (molecule!!.state().value.energy + photon!!.state().value.energy < energyThreshold) {
            // молекула поглощает энергию, пока не достигнет энергетического порога
            return ReactionOutcome(
                consumed = listOf(photon!!),
                updateState = listOf { molecule!!.addEnergy(photon!!.state().value.energy) },
            )
        } else {
            // фотон разбивает молекулу водорода, образуются два атома.
            val freeEnergy = molecule!!.state().value.energy + photon!!.state().value.energy - energyThreshold
            val firstResultElementDirection = randomDirection()
            val secondResultElementDirection = Vec2D(-1 * firstResultElementDirection.x, -1 * firstResultElementDirection.y)
            return ReactionOutcome(
                consumed = listOf(photon!!, molecule!!),
                spawn = listOf {
                    entityGenerator.createEntity(
                        resultElement1,
                        molecule!!.state().value.position.plus(Position(-1.5f * resultElement1.radius, 0f)),
                        firstResultElementDirection,
                        molecule!!.state().value.velocity,
                        energy = 0f
                    )
                    entityGenerator.createEntity(
                        resultElement2,
                        molecule!!.state().value.position.plus(Position(1.5f * resultElement2.radius, 0f)),
                        secondResultElementDirection,
                        0.8f * freeEnergy,
                        energy = 0.2f * freeEnergy
                    )
                },
            )
        }

    }


}