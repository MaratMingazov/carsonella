package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome


// альфа-захват
// «Внутри звезды ион ловит ⁴He, превращается в более тяжёлый элемент».
class StarAlphaGammaReaction(
    private val entityGenerator: IEntityGenerator,      // вот сюда нужно будет передать лямбду, с помощью которой можно создать молекулу водорода H2
) : AtomReactionRule() {
    override val id = "AlphaReaction"

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
        if (firstSpecies !is Species.Atomic) return null
        val firstAtomElement = firstSpecies.element
        if (firstAtomElement.details.alphaGammaResult == null) return null // значит элемент не участвует в альфа захвате

        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filter {
                val sp = it.state().value.species
                sp is Species.Atomic && sp.element == HELIUM_4
            }
            .filter { it.state().value.alive }
            .map { it to  it.state().value.position.distanceSquareTo(firstAtomPosition)}
            .minByOrNull { it.second }
            ?: return null

        if (firstAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return null
        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return null
        val secondSpecies = secondAtom.state().value.species
        if (secondSpecies !is Species.Atomic) return null
        val secondAtomElement = secondSpecies.element

        return if (distanceSquare < firstAtomElement.details.radius * secondAtomElement.details.radius * 2f) {
            Match(firstAtom, secondAtom, firstAtomElement, secondAtomElement)
        } else {
            null
        }
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (atom1, atom2, atom1Element, atom2Element) = match as Match

        val (direction,velocity) = calculateNewEntityDirectionAndVelocity(atom1, atom2)
        val resultPosition = atom1.state().value.position
        val resultElement = atom1Element.details.alphaGammaResult!!
        // Перенос электронной оболочки на продукт (2C2): наследует электроны родителя-ядра,
        // но не больше своего Z. (α,γ) повышает Z → кламп здесь no-op, shake-off не нужен.
        val resultElectrons = minOf(atom1.state().value.electrons, resultElement.details.p)
        val resultPhotonEnergy = 1000f


        return ReactionOutcome(
            consumed = listOf(atom1, atom2),
            spawn = listOf {
                entityGenerator.createEntity(
                    resultElement,
                    resultPosition,
                    direction,
                    velocity,
                    energy = 0f,
                    atom1.getEnvironment(),
                    electrons = resultElectrons,
                )
                entityGenerator.createEntity(
                    Element.PHOTON,
                    Position(
                        resultPosition.x + 1.5f * direction.x * resultElement.details.radius,
                        resultPosition.y + 1.5f * direction.y * resultElement.details.radius
                    ),
                    direction,
                    MAX_VELOCITY,
                    energy = resultPhotonEnergy,
                    environment = atom1.getEnvironment(),
                    electrons = 0,
                )
            },
            description = "$id: ${atom1Element.symbol(atom1.state().value.electrons)} + ${atom2Element.symbol(atom2.state().value.electrons)} -> ${
                resultElement.symbol(
                    resultElectrons
                )
            } + ${Element.PHOTON.details.symbol} [$resultPhotonEnergy ev]"
        )
    }
}
