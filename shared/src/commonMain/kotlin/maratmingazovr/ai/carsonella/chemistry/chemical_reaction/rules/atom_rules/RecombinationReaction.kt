package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.SubAtom
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
import maratmingazovr.ai.carsonella.chemistry.canGainElectron
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StateUpdate

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
        val firstAtom = reagents.first() as? Atom ?: return null
        val firstAtomPosition = firstAtom.state().value.kinematics.position
        if (!firstAtom.state().value.alive) return null
        val firstAtomElement = firstAtom.element
        val firstElectrons = firstAtom.state().value.electrons
        if (!canGainElectron(firstAtomElement, firstElectrons)) return null // значит элемент не участвует в рекомбинации
        // уровни состояния-результата (на 1 электрон больше)
        val recombinedLevels = firstAtomElement.energyLevels(firstElectrons + 1)
        if (recombinedLevels.isEmpty()) return null

        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filterIsInstance<SubAtom>()
            .filter { it.element == ELECTRON }
            .filter { it.state().value.alive }
            .map { it to  it.state().value.kinematics.position.distanceSquareTo(firstAtomPosition)}
            .minByOrNull { it.second }
            ?: return null

        if (firstAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Space) return null
        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Space) return null
        val secondAtomElement = secondAtom.element

        return if (distanceSquare < firstAtomElement.details.radius * secondAtomElement.details.radius * 2f) {
            Match(firstAtom, secondAtom, firstAtomElement, secondAtomElement)
        } else {
            null
        }
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (atom1, atom2, atom1Element, atom2Element) = match as Match
        val electrons = atom1.state().value.electrons
        val resultPosition = atom1.state().value.kinematics.position
        val env = atom1.getEnvironment()

        // Ион ловит электрон: Element НЕ меняется — updateState(electrons+1, energy=0), вылетает фотон.
        // Голый протон здесь ничем не особен: это H с 0 электронов, и он становится H с одним.
        val resultElectrons = electrons + 1
        val photonEnergy = atom1Element.energyLevels(resultElectrons).last()
        val direction = atom1.state().value.kinematics.direction
        val radius = atom1Element.details.radius
        return ReactionOutcome(
            consumed = listOf(atom2),
            updateState = listOf(StateUpdate(atom1) {
                atom1.setElectrons(resultElectrons)
                // Электрон сел сразу в основное состояние (фотон унёс энергию связи). Сбрасываем energy в 0:
                // старая энергия иона для нового заряда не валидна (инвариант Atom на updateState-пути).
                atom1.setEnergy(0f)
            }),
            spawn = listOf {
                entityGenerator.createEntity(
                    Element.PHOTON,
                    Position(
                        resultPosition.x + 1.5f * direction.x * radius,
                        resultPosition.y + 1.5f * direction.y * radius
                    ),
                    direction, MAX_VELOCITY, energy = photonEnergy, environment = env, electrons = 0,
                )
            },)
    }
}
