package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.Photon
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule

class AtomPlusAtomToMolecule(
    private val entityGenerator: IEntityGenerator,      // вот сюда нужно будет передать лямбду, с помощью которой можно создать молекулу водорода H2
    private val element1: Element,
    private val element2: Element,
    private val resultElement: Element,
    private val resultPhotonEnergy: Float = 0f, // в результате реакции может выделяться энергия в виде фотонов в эВ.
    private val temperatureMode: TemperatureMode = TemperatureMode.Space,
    private val resultElement2: Element? = null,
    private val resultElement3: Element? = null,
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

        if (firstAtom.getEnvironment().getEnvTemperature() != temperatureMode) return false
        if (secondAtom.getEnvironment().getEnvTemperature() != temperatureMode) return false

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
        val spawnList = mutableListOf<() -> Entity<*>>()
        val resultPosition = atom1!!.state().value.position
        val atom1Element = atom1!!.state().value.element
        val atom2Element = atom2!!.state().value.element

        spawnList += {
            entityGenerator.createEntity(
                resultElement,
                resultPosition,
                direction,
                velocity,
                energy = atom1!!.state().value.energy + atom2!!.state().value.energy,
                atom1!!.getEnvironment(),
            )
        }

        if (resultElement2 != null) {
            spawnList += {
                entityGenerator.createEntity(
                    resultElement2,
                    Position(resultPosition.x + 1.5f * direction.x * resultElement.radius,resultPosition.y),
                    direction,
                    velocity,
                    energy = 0f,
                )
            }

        }

        if (resultElement3 != null) {
            spawnList += {
                entityGenerator.createEntity(
                    resultElement3,
                    Position(resultPosition.x - 1.5f * direction.x * resultElement.radius,resultPosition.y),
                    direction,
                    velocity,
                    energy = 0f,
                )
            }

        }

        if (resultPhotonEnergy > 0) {
            spawnList += {
                entityGenerator.createEntity(
                    Photon,
                    Position(resultPosition.x + 1.5f * direction.x * resultElement.radius,resultPosition.y + 1.5f * direction.y * resultElement.radius),
                    direction,
                    10f,
                    energy = resultPhotonEnergy,
                )
            }

        }

        return ReactionOutcome(
            consumed = listOf(atom1!!, atom2!!),
            spawn = spawnList,
            description = "${atom1Element.symbol} + ${atom2Element.symbol} -> ${resultElement.symbol} + ${Photon.symbol}"
        )
    }
}