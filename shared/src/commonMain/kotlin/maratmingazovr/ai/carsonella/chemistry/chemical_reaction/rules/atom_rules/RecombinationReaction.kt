package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.canGainElectron
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome

// «Ион ловит электрон, излучает фотон».
class RecombinationReaction(
    private val entityGenerator: IEntityGenerator,
) : AtomReactionRule() {
    override val id = "Recombination"

    /** [atom1Element]/[atom2Element] выяснены в matchesAtoms — produce не вычисляет заново. */
    private data class Match(
        val atom1: Entity,
        val atom2: Entity,
        val atom1Element: Element,
        val atom2Element: Element,
    ) : MatchedData

    override fun matchesAtoms(reagents: List<Entity>) : MatchedData? {
        if (reagents.size < 2) return null
        val firstAtom = reagents.first()
        val firstAtomPosition = reagents.first().state().value.position
        if (!firstAtom.state().value.alive) return null
        // species в локальный val → smart-cast к Elemental ниже (через Entity компилятор сам этого не знает).
        val firstSpecies = firstAtom.state().value.species
        if (firstSpecies !is Species.Elemental) return null
        val firstAtomElement = firstSpecies.element
        val firstElectrons = firstAtom.state().value.electrons
        if (!canGainElectron(firstAtomElement, firstElectrons)) return null // значит элемент не участвует в рекомбинации
        // уровни состояния-результата (на 1 электрон больше); для протона результат — HYDROGEN
        val recombinedLevels = if (firstAtomElement == Element.Proton) Element.HYDROGEN.energyLevels(1)
                               else firstAtomElement.energyLevels(firstElectrons + 1)
        if (recombinedLevels.isEmpty()) return null

        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filter {
                val sp = it.state().value.species
                sp is Species.Elemental && sp.element == ELECTRON
            }
            .filter { it.state().value.alive }
            .map { it to  it.state().value.position.distanceSquareTo(firstAtomPosition)}
            .minByOrNull { it.second }
            ?: return null

        if (firstAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Space) return null
        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Space) return null
        val secondSpecies = secondAtom.state().value.species
        if (secondSpecies !is Species.Elemental) return null
        val secondAtomElement = secondSpecies.element

        return if (distanceSquare < firstAtomElement.details.radius * secondAtomElement.details.radius * 2f) {
            Match(firstAtom, secondAtom, firstAtomElement, secondAtomElement)
        } else {
            null
        }
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (atom1, atom2, atom1Element, atom2Element) = match as Match
        val electrons = atom1.state().value.electrons
        val resultPosition = atom1.state().value.position
        val env = atom1.getEnvironment()

        // Протий — особый случай: p⁺ + e⁻ → HYDROGEN (атом). Element/класс меняется (element неизменяем) →
        // consume + spawn (со слиянием импульса протона и электрона).
        if (atom1Element == Element.Proton) {
            val (direction, velocity) = calculateNewEntityDirectionAndVelocity(atom1, atom2)
            val photonEnergy = Element.HYDROGEN.energyLevels(1).last()
            val radius = Element.HYDROGEN.details.radius
            return ReactionOutcome(
                consumed = listOf(atom1, atom2),
                spawn = listOf {
                    entityGenerator.createEntity(
                        Element.HYDROGEN, resultPosition, direction, velocity,
                        energy = 0f, env, electrons = 1,
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

        // Обычный ион ловит электрон: Element НЕ меняется — updateState(electrons+1, energy=0), вылетает фотон.
        val resultElectrons = electrons + 1
        val photonEnergy = atom1Element.energyLevels(resultElectrons).last()
        val direction = atom1.state().value.direction
        val radius = atom1Element.details.radius
        return ReactionOutcome(
            consumed = listOf(atom2),
            updateState = listOf {
                atom1.setElectrons(resultElectrons)
                // Электрон сел сразу в основное состояние (фотон унёс энергию связи). Сбрасываем energy в 0:
                // старая энергия иона для нового заряда не валидна (инвариант Atom на updateState-пути).
                atom1.setEnergy(0f)
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
