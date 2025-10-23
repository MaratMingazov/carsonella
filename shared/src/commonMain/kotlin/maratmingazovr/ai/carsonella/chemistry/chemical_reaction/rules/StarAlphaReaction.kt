package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.HE_4_ION_2
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator

class StarAlphaReaction(
    private val entityGenerator: IEntityGenerator,      // вот сюда нужно будет передать лямбду, с помощью которой можно создать молекулу водорода H2
) : ReactionRule {
    override val id = ""

    private var atom1 : Entity<*>? = null
    private var atom2 : Entity<*>? = null

    override suspend fun matches(reagents: List<Entity<*>>) : Boolean {
        atom1 = null
        atom2 = null
        if (reagents.size < 2) return false
        val firstAtom = reagents.first()
        val firstAtomPosition = reagents.first().state().value.position
        val firstAtomElement = firstAtom.state().value.element
        if (!firstAtom.state().value.alive) return false
        if (firstAtomElement.details.alphaReactionResult == null) return false // значит элемент не участвует в альфа захвате

        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filter { it.state().value.element == HE_4_ION_2 }
            .filter { it.state().value.alive }
            .map { it to  it.state().value.position.distanceSquareTo(firstAtomPosition)}
            .minByOrNull { it.second }
            ?: return false

        if (firstAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false
        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false
        val secondAtomElement = secondAtom.state().value.element

        return if (distanceSquare < firstAtomElement.details.radius * secondAtomElement.details.radius * 2f) {
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
        val resultPosition = atom1!!.state().value.position
        val atom1Element = atom1!!.state().value.element
        val atom2Element = atom2!!.state().value.element
        val resultElement = atom1Element.details.alphaReactionResult!!
        val resultPhotonEnergy = 1000f


        return ReactionOutcome(
            consumed = listOf(atom1!!, atom2!!),
            spawn = listOf {
                entityGenerator.createEntity(
                    resultElement,
                    resultPosition,
                    direction,
                    velocity,
                    energy = atom1!!.state().value.energy + atom2!!.state().value.energy,
                    atom1!!.getEnvironment(),
                )
                entityGenerator.createEntity(
                    Element.Photon,
                    Position(
                        resultPosition.x + 1.5f * direction.x * resultElement.details.radius,
                        resultPosition.y + 1.5f * direction.y * resultElement.details.radius
                    ),
                    direction,
                    10f,
                    energy = resultPhotonEnergy,
                )
            },
            description = "${atom1Element.details.symbol} + ${atom2Element.details.symbol} -> ${resultElement.details.symbol} + ${Element.Photon.details.symbol} (alpha reaction)"
        )
    }
}