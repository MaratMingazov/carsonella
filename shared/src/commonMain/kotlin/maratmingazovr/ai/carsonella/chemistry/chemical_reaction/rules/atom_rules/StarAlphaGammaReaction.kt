package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome


// альфа-захват
// «Внутри звезды ион ловит ⁴He, превращается в более тяжёлый элемент».
class StarAlphaGammaReaction(
    private val entityGenerator: IEntityGenerator,      // вот сюда нужно будет передать лямбду, с помощью которой можно создать молекулу водорода H2
) : AtomReactionRule() {
    override val id = "AlphaReaction"

    /** [atom1Element]/[atom2Element] выяснены в matchesAtom — produce не вычисляет заново. */
    private data class Match(
        val atom1: Entity,
        val atom2: Entity,
        val atom1Element: Element,
        val atom2Element: Element,
    ) : MatchedData

    override fun matchesAtom(atom: Atom, neighbors: List<Entity>): MatchedData? {
        if (neighbors.isEmpty()) return null
        val atomPosition = atom.state().value.kinematics.position
        val atomElement = atom.element
        if (atomElement.details.alphaGammaResult == null) return null // значит элемент не участвует в альфа захвате

        val (secondAtom, distanceSquare) = neighbors
            .filterIsInstance<Atom>()
            .filter { it.element == HELIUM_4 }
            .filter { it.state().value.alive }
            .map { it to  it.state().value.kinematics.position.distanceSquareTo(atomPosition)}
            .minByOrNull { it.second }
            ?: return null

        if (atom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return null
        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return null
        val secondAtomElement = secondAtom.element

        return if (distanceSquare < atomElement.details.radius * secondAtomElement.details.radius * 2f) {
            Match(atom, secondAtom, atomElement, secondAtomElement)
        } else {
            null
        }
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (atom1, atom2, atom1Element, atom2Element) = match as Match

        val (direction,velocity) = calculateNewEntityDirectionAndVelocity(atom1, atom2)
        val resultPosition = atom1.state().value.kinematics.position
        val resultElement = atom1Element.details.alphaGammaResult!!
        // Перенос электронной оболочки на продукт (2C2): наследует электроны родителя-ядра,
        // но не больше своего Z. (α,γ) повышает Z → кламп здесь no-op, shake-off не нужен.
        val resultElectrons = minOf(atom1.state().value.electrons, resultElement.details.p)
        val resultPhotonEnergy = 1000f


        return ReactionOutcome(
            consumed = listOf(atom1, atom2),
            // Лямбда на КАЖДЫЙ продукт: мир собирает описание из их результатов, а из одной лямбды
            // с двумя createEntity наружу вернулась бы только вторая сущность.
            spawn = listOf(
                {
                    entityGenerator.createEntity(
                        resultElement,
                        resultPosition,
                        direction,
                        velocity,
                        energy = 0f,
                        atom1.getEnvironment(),
                        electrons = resultElectrons,
                    )
                },
                {
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
            ),)
    }
}
