package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4_ION_2
import maratmingazovr.ai.carsonella.chemistry.Element.NEUTRON
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator

/**
 * (n,α) — захват нейтрона с испусканием α-частицы (⁴He):
 *
 *   A + n → A′ + ⁴He   (Z → Z−2, A → A−3)
 *
 * Третий нейтронный канал рядом с [StarNeutronGammaReaction] (n,γ) и
 * [StarNeutronProtonReaction] (n,p): тут после захвата вылетает целое ядро гелия,
 * так что target проваливается сразу на два Z вниз.
 *
 * Реализованный target — **¹⁷O(n,α)¹⁴C**: тепловые нейтроны выбивают α из кислорода-17.
 * Продукт ¹⁴C β⁻-нестабилен, поэтому реакция вместе с [BetaMinusDecay] кормит ту же
 * радиоуглеродную петлю, что и (n,p): ¹⁴C(β⁻)¹⁴N. На ¹⁷O конкурирует с (n,γ)→¹⁸O —
 * выбор канала случайный (`weight()=0f`), оба наблюдаемы.
 *
 * Прочие хрестоматийные (n,α) — ¹⁰B(n,α)⁷Li (нейтронозахватная терапия), ⁶Li(n,α)³H —
 * ждут появления target-ядер ¹⁰B, ⁶Li (источники в spallation/BBN).
 *
 * **Нет кулоновского барьера на входе** (нейтрон нейтрален). Пока ограничено
 * TemperatureMode.Star для согласованности с (n,γ)/(n,p)/(α,n).
 *
 * Электронный баланс тривиален: target и продукт — голые ядра (e=0), α вылетает как
 * ⁴He²⁺ (e=0). Заряд сходится: A^Z⁺ + n → A′^(Z−2)⁺ + ⁴He²⁺.
 *
 * Generic-правило: триггерится по полю Details.neutronAlphaResult.
 */
class StarNeutronAlphaReaction(
    private val entityGenerator: IEntityGenerator,
) : ReactionRule {
    override val id = "StarNeutronAlphaReaction"

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
        if (firstAtomElement.details.neutronAlphaResult == null) return false

        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filter { it.state().value.element == NEUTRON }
            .filter { it.state().value.alive }
            .map { it to it.state().value.position.distanceSquareTo(firstAtomPosition) }
            .minByOrNull { it.second }
            ?: return false

        if (firstAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false
        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false
        if (distanceSquare >= firstAtomElement.details.radius * NEUTRON.details.radius * 2f) return false

        atom1 = firstAtom
        atom2 = secondAtom
        return true
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {
        val a1 = atom1!!
        val a2 = atom2!!
        val (direction, velocity) = calculateNewEntityDirectionAndVelocity(a1, a2)
        val resultPosition = a1.state().value.position
        val atom1Element = a1.state().value.element
        val atom2Element = a2.state().value.element
        val resultElement = atom1Element.details.neutronAlphaResult!!

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
                    // α-отдача вылетает по направлению движения СМ — отдельный degree of
                    // freedom импульса между продуктами в проекте не моделируется (см. StarPPChain).
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