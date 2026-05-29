package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4_ION_2
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator

/**
 * (p,α) — захват протона с испусканием α-частицы в недрах звезды:
 *
 *   A + p → A′ + ⁴He   (Z → Z-1, A → A-3)
 *
 * Главное применение — замыкания каталитических циклов горения водорода:
 *  · CNO-I:   ¹⁵N+p→¹²C+α
 *  · CNO-II:  ¹⁷O+p→¹⁴N+α
 *  · CNO-III: ¹⁸O+p→¹⁵N+α
 *  · CNO-IV:  ¹⁹F+p→¹⁶O+α
 *  · NeNa:    ²³Na+p→²⁰Ne+α
 *  · MgAl:    ²⁷Al+p→²⁴Mg+α
 *
 * Generic-правило: триггерится по полю Details.protonAlphaResult. Работает только в
 * TemperatureMode.Star.
 *
 * Branching с (p,γ) на тех же target-ядрах (¹⁵N/¹⁷O/¹⁸O) решается резолвером равновероятно
 * (`weight()=0f`) — упрощение от реальных T-зависимых соотношений. См. док у
 * StarProtonGammaReaction.
 */
class StarProtonAlphaReaction(
    private val entityGenerator: IEntityGenerator,
) : ReactionRule {
    override val id = "StarProtonAlphaReaction"

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
        if (firstAtomElement.details.protonAlphaResult == null) return false

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
        val resultElement = atom1Element.details.protonAlphaResult!!

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
                    // α-отдача вылетает по направлению СМ (impulse-split не моделируется).
                    entityGenerator.createEntity(
                        HELIUM_4_ION_2,
                        Position(
                            resultPosition.x + 1.5f * direction.x * resultElement.details.radius,
                            resultPosition.y + 1.5f * direction.y * resultElement.details.radius,
                        ),
                        direction,
                        20f,
                        energy = 0f,
                        environment = a1.getEnvironment(),
                    )
                },
            ),
            description = "$id: ${atom1Element.details.symbol} + ${atom2Element.details.symbol} → ${resultElement.details.symbol} + ${HELIUM_4_ION_2.details.symbol}",
        )
    }
}