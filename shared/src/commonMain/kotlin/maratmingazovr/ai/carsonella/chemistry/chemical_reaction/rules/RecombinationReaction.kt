package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.canGainElectron
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator

// «Ион ловит электрон, излучает фотон».
class RecombinationReaction(
    private val entityGenerator: IEntityGenerator,
) : ReactionRule {
    override val id = "Recombination"

    private var atom1 : Entity<*>? = null
    private var atom2 : Entity<*>? = null

    override fun matches(reagents: List<Entity<*>>) : Boolean {
        atom1 = null
        atom2 = null
        if (reagents.size < 2) return false
        val firstAtom = reagents.first()
        val firstAtomPosition = reagents.first().state().value.position
        val firstAtomElement = firstAtom.state().value.element
        if (!firstAtom.state().value.alive) return false
        val firstElectrons = firstAtom.state().value.electrons
        if (!canGainElectron(firstAtomElement, firstElectrons)) return false // значит элемент не участвует в рекомбинации
        // уровни состояния-результата (на 1 электрон больше); для протона результат — HYDROGEN
        val recombinedLevels = if (firstAtomElement == Element.Proton) Element.HYDROGEN.energyLevels(1)
                               else firstAtomElement.energyLevels(firstElectrons + 1)
        if (recombinedLevels.isEmpty()) return false

        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filter { it.state().value.element == ELECTRON }
            .filter { it.state().value.alive }
            .map { it to  it.state().value.position.distanceSquareTo(firstAtomPosition)}
            .minByOrNull { it.second }
            ?: return false

        if (firstAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Space) return false
        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Space) return false
        val secondAtomElement = secondAtom.state().value.element

        return if (distanceSquare < firstAtomElement.details.radius * secondAtomElement.details.radius * 2f) {
            atom1 = firstAtom
            atom2 = secondAtom
            true
        } else {
            false
        }
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {

        val atom1Element = atom1!!.state().value.element
        val atom2Element = atom2!!.state().value.element
        val electrons = atom1!!.state().value.electrons
        val resultPosition = atom1!!.state().value.position
        val electronEnergy = atom2!!.state().value.energy
        val env = atom1!!.getEnvironment()

        // Протий — особый случай: p⁺ + e⁻ → HYDROGEN (атом). Element/класс меняется (element неизменяем) →
        // consume + spawn (со слиянием импульса протона и электрона).
        if (atom1Element == Element.Proton) {
            val (direction, velocity) = calculateNewEntityDirectionAndVelocity(atom1!!, atom2!!)
            val photonEnergy = Element.HYDROGEN.energyLevels(1).last()
            val radius = Element.HYDROGEN.details.radius
            return ReactionOutcome(
                consumed = listOf(atom1!!, atom2!!),
                spawn = listOf {
                    entityGenerator.createEntity(
                        Element.HYDROGEN, resultPosition, direction, velocity,
                        energy = atom1!!.state().value.energy + electronEnergy, env,
                    )
                    entityGenerator.createEntity(
                        Element.PHOTON,
                        Position(resultPosition.x + 1.5f * direction.x * radius, resultPosition.y + 1.5f * direction.y * radius),
                        direction, 10f, energy = photonEnergy, environment = env,
                    )
                },
                description = "$id: ${atom1Element.details.symbol} + ${atom2Element.details.symbol} -> ${Element.HYDROGEN.symbol(Element.HYDROGEN.details.e)} + ${Element.PHOTON.details.symbol} [$photonEnergy ev]"
            )
        }

        // Обычный ион ловит электрон: Element НЕ меняется — updateState(electrons+1, +энергия e⁻), вылетает фотон.
        val resultElectrons = electrons + 1
        val photonEnergy = atom1Element.energyLevels(resultElectrons).last()
        val direction = atom1!!.state().value.direction
        val radius = atom1Element.details.radius
        return ReactionOutcome(
            consumed = listOf(atom2!!),
            updateState = listOf {
                atom1!!.setElectrons(resultElectrons)
                atom1!!.addEnergy(electronEnergy)
            },
            spawn = listOf {
                entityGenerator.createEntity(
                    Element.PHOTON,
                    Position(resultPosition.x + 1.5f * direction.x * radius, resultPosition.y + 1.5f * direction.y * radius),
                    direction, 10f, energy = photonEnergy, environment = env,
                )
            },
            description = "$id: ${atom1Element.symbol(electrons)} + ${atom2Element.details.symbol} -> ${atom1Element.symbol(resultElectrons)} + ${Element.PHOTON.details.symbol} [$photonEnergy ev]"
        )
    }
}
