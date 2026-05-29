package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator

/**
 * (p,γ) — радиативный захват протона ядром в недрах звезды:
 *
 *   A + p → A′ + γ   (Z → Z+1, A → A+1)
 *
 * Тип реакции, встречающийся в:
 *  · CNO/NeNa/MgAl-циклах горения водорода (²⁰Ne+p→²¹Na, ²⁴Mg+p→²⁵Al, ²²Ne+p→²³Na и т.п.)
 *  · pp-III (⁷Be+p→⁸B)
 *  · hot CNO breakouts (¹³N+p→¹⁴O при высокой T)
 *
 * Generic-правило: триггерится по полю Details.protonGammaResult. Работает только в
 * TemperatureMode.Star — нужен достаточный кинетический импульс протона для преодоления
 * кулоновского барьера ядра, а это происходит при звёздных T.
 *
 * Сейчас CNO-I/II/III/IV живёт в отдельном захардкоженном правиле StarCNOCycle (там branching
 * через chance() на ¹⁵N/¹⁷O/¹⁸O). Это правило используется для NeNa/MgAl циклов; CNO в него
 * мигрирует отдельной задачей.
 */
class StarProtonGammaReaction(
    private val entityGenerator: IEntityGenerator,
) : ReactionRule {
    override val id = "StarProtonGammaReaction"

    private var atom1: Entity<*>? = null
    private var atom2: Entity<*>? = null

    override fun matches(reagents: List<Entity<*>>): Boolean {
        atom1 = null
        atom2 = null
        if (reagents.size < 2) return false
        val firstAtom = reagents.first()
        val firstAtomPosition = firstAtom.state().value.position
        val firstAtomElement = firstAtom.state().value.element
        if (!firstAtom.state().value.alive) return false
        if (firstAtomElement.details.protonGammaResult == null) return false

        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filter { it.state().value.element == Proton }
            .filter { it.state().value.alive }
            .map { it to it.state().value.position.distanceSquareTo(firstAtomPosition) }
            .minByOrNull { it.second }
            ?: return false

        if (firstAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false
        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false

        return if (distanceSquare < firstAtomElement.details.radius * Proton.details.radius * 2f) {
            atom1 = firstAtom
            atom2 = secondAtom
            true
        } else {
            false
        }
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {
        val a1 = atom1!!
        val a2 = atom2!!
        val (direction, velocity) = calculateNewEntityDirectionAndVelocity(a1, a2)
        val resultPosition = a1.state().value.position
        val atom1Element = a1.state().value.element
        val atom2Element = a2.state().value.element
        val resultElement = atom1Element.details.protonGammaResult!!
        val resultPhotonEnergy = 1000f

        return ReactionOutcome(
            consumed = listOf(a1, a2),
            spawn = listOf(
                {
                    entityGenerator.createEntity(
                        resultElement,
                        resultPosition,
                        direction,
                        velocity,
                        energy = a1.state().value.energy + a2.state().value.energy,
                        a1.getEnvironment(),
                    )
                },
                {
                    entityGenerator.createEntity(
                        Element.PHOTON,
                        Position(
                            resultPosition.x + 1.5f * direction.x * resultElement.details.radius,
                            resultPosition.y + 1.5f * direction.y * resultElement.details.radius,
                        ),
                        direction,
                        10f,
                        energy = resultPhotonEnergy,
                        environment = a1.getEnvironment(),
                    )
                },
            ),
            description = "$id: ${atom1Element.details.symbol} + ${atom2Element.details.symbol} → ${resultElement.details.symbol} + ${Element.PHOTON.details.symbol} [$resultPhotonEnergy ev]",
        )
    }
}