package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.canGainElectron
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome

// «Ион ловит электрон, излучает фотон».
class RecombinationReaction(
    private val entityGenerator: IEntityGenerator,
) : AtomReactionRule() {
    override val id = "Recombination"

    private var atom1 : Entity? = null
    private var atom2 : Entity? = null
    private var atom1El : Element? = null   // элементы атомов, запомненные в matchesAtoms — produce не вычисляет заново
    private var atom2El : Element? = null

    override fun matchesAtoms(reagents: List<Entity>) : Boolean {
        atom1 = null
        atom2 = null
        atom1El = null
        atom2El = null
        if (reagents.size < 2) return false
        val firstAtom = reagents.first()
        val firstAtomPosition = reagents.first().state().value.position
        if (!firstAtom.state().value.alive) return false
        // species в локальный val → smart-cast к Elemental ниже (через Entity компилятор сам этого не знает).
        val firstSpecies = firstAtom.state().value.species
        if (firstSpecies !is Species.Elemental) return false
        val firstAtomElement = firstSpecies.element
        val firstElectrons = firstAtom.state().value.electrons
        if (!canGainElectron(firstAtomElement, firstElectrons)) return false // значит элемент не участвует в рекомбинации
        // уровни состояния-результата (на 1 электрон больше); для протона результат — HYDROGEN
        val recombinedLevels = if (firstAtomElement == Element.Proton) Element.HYDROGEN.energyLevels(1)
                               else firstAtomElement.energyLevels(firstElectrons + 1)
        if (recombinedLevels.isEmpty()) return false

        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filter {
                val sp = it.state().value.species
                sp is Species.Elemental && sp.element == ELECTRON
            }
            .filter { it.state().value.alive }
            .map { it to  it.state().value.position.distanceSquareTo(firstAtomPosition)}
            .minByOrNull { it.second }
            ?: return false

        if (firstAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Space) return false
        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Space) return false
        val secondSpecies = secondAtom.state().value.species
        if (secondSpecies !is Species.Elemental) return false
        val secondAtomElement = secondSpecies.element

        return if (distanceSquare < firstAtomElement.details.radius * secondAtomElement.details.radius * 2f) {
            atom1 = firstAtom
            atom2 = secondAtom
            atom1El = firstAtomElement
            atom2El = secondAtomElement
            true
        } else {
            false
        }
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {

        val atom1Element = atom1El!!   // запомнили в matchesAtoms
        val atom2Element = atom2El!!
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
                        energy = atom1!!.state().value.energy + electronEnergy, env, electrons = 1,
                    )
                    entityGenerator.createEntity(
                        Element.PHOTON,
                        Position(
                            resultPosition.x + 1.5f * direction.x * radius,
                            resultPosition.y + 1.5f * direction.y * radius
                        ),
                        direction, 10f, energy = photonEnergy, environment = env, electrons = 0,
                    )
                },
                description = "$id: ${atom1Element.details.symbol} + ${atom2Element.details.symbol} -> ${
                    Element.HYDROGEN.symbol(
                        1
                    )
                } + ${Element.PHOTON.details.symbol} [$photonEnergy ev]"
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
                    Position(
                        resultPosition.x + 1.5f * direction.x * radius,
                        resultPosition.y + 1.5f * direction.y * radius
                    ),
                    direction, 10f, energy = photonEnergy, environment = env, electrons = 0,
                )
            },
            description = "$id: ${atom1Element.symbol(electrons)} + ${atom2Element.details.symbol} -> ${
                atom1Element.symbol(
                    resultElectrons
                )
            } + ${Element.PHOTON.details.symbol} [$photonEnergy ev]"
        )
    }
}
