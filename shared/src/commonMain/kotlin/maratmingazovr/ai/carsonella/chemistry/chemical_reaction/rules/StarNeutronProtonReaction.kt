package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Element.NEUTRON
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.randomDirection

/**
 * (n,p) — захват нейтрона с испусканием протона:
 *
 *   A + n → A′ + p   (Z → Z−1, A неизменно)
 *
 * Изобарный переход вниз по Z. Зеркало [StarNeutronGammaReaction]: тот после захвата
 * сбрасывает γ и остаётся на том же Z (A→A+1), этот выбивает протон и уходит на Z−1.
 *
 * Канонический пример — **¹⁴N(n,p)¹⁴C**: именно так в атмосфере рождается радиоуглерод
 * (нейтроны от космических лучей бьют по азоту), а в звёздах это заметный «нейтронный яд».
 * Продукт ¹⁴C β⁻-нестабилен (¹⁴C → ¹⁴N + e⁻), так что реакция вместе с [BetaMinusDecay]
 * замыкает петлю ¹⁴N(n,p)¹⁴C(β⁻)¹⁴N — нейтрон в итоге «съедается», азот восстанавливается.
 *
 * **Нет кулоновского барьера на входе** (нейтрон нейтрален), поэтому (n,p) идёт и при
 * умеренных T. Пока ограничено TemperatureMode.Star для согласованности с (n,γ)/(α,n).
 *
 * Электронный баланс тривиален: и target, и продукт — голые ядра (e=0), вылетающий протон
 * тоже без электрона. Заряд сходится: A^Z⁺ + n → A′^(Z−1)⁺ + p⁺.
 *
 * Generic-правило: триггерится по полю Details.neutronProtonResult.
 */
class StarNeutronProtonReaction(
    private val entityGenerator: IEntityGenerator,
) : ReactionRule {
    override val id = "StarNeutronProtonReaction"

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
        if (firstAtomElement.details.neutronProtonResult == null) return false

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
        val resultElement = atom1Element.details.neutronProtonResult!!
        // Перенос электронной оболочки на продукт (2C2): (n,p) понижает Z на 1 → если родитель почти
        // нейтрален, лишний электрон не помещается на продукт и улетает свободным e⁻ (shake-off).
        val parentElectrons = a1.state().value.electrons
        val resultElectrons = minOf(parentElectrons, resultElement.details.p)
        val shakeOff = parentElectrons - resultElectrons

        val spawnList = mutableListOf<() -> Entity<*>>()
        spawnList += {
            entityGenerator.createEntity(
                resultElement,
                resultPosition,
                direction,
                velocity,
                energy = a1.state().value.energy + a2.state().value.energy,
                a1.getEnvironment(),
                electrons = resultElectrons,
            )
        }
        spawnList += {
            // Протон-отдача вылетает по направлению движения СМ — отдельный degree of
            // freedom импульса между продуктами в проекте не моделируется (см. StarPPChain).
            entityGenerator.createEntity(
                Proton,
                Position(
                    resultPosition.x + 1.5f * direction.x * resultElement.details.radius,
                    resultPosition.y + 1.5f * direction.y * resultElement.details.radius,
                ),
                direction,
                20f,
                energy = 0f,
                environment = a1.getEnvironment(),
                electrons = 0,
            )
        }
        repeat(shakeOff) {
            spawnList += {
                entityGenerator.createEntity(
                    ELECTRON,
                    Position(resultPosition.x, resultPosition.y + resultElement.details.radius),
                    randomDirection(entityGenerator.random),
                    20f,
                    energy = 0f,
                    environment = a1.getEnvironment(),
                    electrons = 1,
                )
            }
        }

        val electronTail = if (shakeOff > 0) " + $shakeOff${ELECTRON.details.symbol}" else ""
        return ReactionOutcome(
            consumed = listOf(a1, a2),
            spawn = spawnList,
            description = "$id: ${atom1Element.symbol(parentElectrons)} + ${atom2Element.symbol(a2.state().value.electrons)} → ${resultElement.symbol(resultElectrons)} + ${Proton.details.symbol}$electronTail",
        )
    }
}