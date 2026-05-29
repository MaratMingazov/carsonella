package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ALUMINUM_27_ION_13
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4_ION_2
import maratmingazovr.ai.carsonella.chemistry.Element.NITROGEN_14_ION_7
import maratmingazovr.ai.carsonella.chemistry.Element.NITROGEN_15_ION_7
import maratmingazovr.ai.carsonella.chemistry.Element.OXYGEN_16_ION_8
import maratmingazovr.ai.carsonella.chemistry.Element.OXYGEN_17_ION_8
import maratmingazovr.ai.carsonella.chemistry.Element.OXYGEN_18_ION_8
import maratmingazovr.ai.carsonella.chemistry.Element.PHOTON
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Element.SODIUM_23_ION_11
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator

/**
 * Star proton capture — объединённое generic-правило для всех (p,X) реакций в звезде:
 *
 *   A + p → A′ + γ   (p,γ) — радиативный захват, Z→Z+1, A→A+1
 *   A + p → A′ + ⁴He (p,α) — выброс α, Z→Z-1, A→A-3
 *
 * Покрывает все каталитические циклы горения водорода (CNO/NeNa/MgAl), pp-III и
 * hot CNO breakouts. Триггерится по полям `protonGammaResult` и `protonAlphaResult` в
 * Details — структура «что может произойти» хранится там; вероятности «с какой долей и
 * скоростью» захардкожены в этом файле (см. `captureRate` и `branchingWeights`).
 *
 * Алгоритм matches():
 *   1. Найти target + ближайший Proton, проверить контакт и TemperatureMode.Star.
 *   2. Применить `captureRate(target)` — bottleneck/slowdown для конкретных target-ядер.
 *      Если roll не прошёл, реакция в этом тике не идёт.
 *   3. Собрать список доступных исходов (γ и/или α) с весами из `branchingWeights(target)`.
 *      Roulette wheel: один roll выбирает один канал. Это математически корректно (в
 *      отличие от двух независимых правил, где случались "оба сработали" / "ни один").
 *
 * Расширение до (p,n) — добавить ветку `Outcome.Neutron`, поле `protonNeutronResult` в
 * Details, третий вес в `branchingWeights`. ⁷Li(p,n)⁷Be — главный кандидат для start'а.
 */
class StarProtonCaptureReaction(
    private val entityGenerator: IEntityGenerator,
) : ReactionRule {
    override val id = "StarProtonCaptureReaction"

    private sealed class Outcome {
        data class Gamma(val product: Element) : Outcome()
        data class Alpha(val product: Element) : Outcome()
    }

    private var atom1: Entity<*>? = null
    private var atom2: Entity<*>? = null
    private var chosenOutcome: Outcome? = null

    override fun matches(reagents: List<Entity<*>>): Boolean {
        atom1 = null
        atom2 = null
        chosenOutcome = null
        if (reagents.size < 2) return false
        val firstAtom = reagents.first()
        val firstAtomPosition = firstAtom.state().value.position
        val firstAtomElement = firstAtom.state().value.element
        if (!firstAtom.state().value.alive) return false

        val gammaResult = firstAtomElement.details.protonGammaResult
        val alphaResult = firstAtomElement.details.protonAlphaResult
        if (gammaResult == null && alphaResult == null) return false

        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filter { it.state().value.element == Proton }
            .filter { it.state().value.alive }
            .map { it to it.state().value.position.distanceSquareTo(firstAtomPosition) }
            .minByOrNull { it.second }
            ?: return false

        if (firstAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false
        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false
        if (distanceSquare >= firstAtomElement.details.radius * Proton.details.radius * 2f) return false

        // Reaction rate — bottleneck/slowdown для конкретных target-ядер.
        if (!chance(captureRate(firstAtomElement), entityGenerator.random)) return false

        // Branching — roulette wheel по доступным каналам.
        val (gW, aW) = branchingWeights(firstAtomElement)
        val candidates = mutableListOf<Pair<Outcome, Float>>()
        if (gammaResult != null && gW > 0f) candidates += (Outcome.Gamma(gammaResult) as Outcome) to gW
        if (alphaResult != null && aW > 0f) candidates += (Outcome.Alpha(alphaResult) as Outcome) to aW
        if (candidates.isEmpty()) return false

        val total = candidates.fold(0f) { acc, p -> acc + p.second }
        val roll = entityGenerator.random.nextFloat() * total
        var cumulative = 0f
        var picked: Outcome = candidates.last().first
        for ((out, w) in candidates) {
            cumulative += w
            if (roll < cumulative) { picked = out; break }
        }

        atom1 = firstAtom
        atom2 = secondAtom
        chosenOutcome = picked
        return true
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {
        val a1 = atom1!!
        val a2 = atom2!!
        val outcome = chosenOutcome!!

        val (direction, velocity) = calculateNewEntityDirectionAndVelocity(a1, a2)
        val resultPosition = a1.state().value.position
        val atom1Element = a1.state().value.element
        val atom2Element = a2.state().value.element

        return when (outcome) {
            is Outcome.Gamma -> {
                val resultElement = outcome.product
                val resultPhotonEnergy = 1000f
                ReactionOutcome(
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
                                PHOTON,
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
                    description = "$id (p,γ): ${atom1Element.details.symbol} + ${atom2Element.details.symbol} → ${resultElement.details.symbol} + ${PHOTON.details.symbol}",
                )
            }
            is Outcome.Alpha -> {
                val resultElement = outcome.product
                ReactionOutcome(
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
                    description = "$id (p,α): ${atom1Element.details.symbol} + ${atom2Element.details.symbol} → ${resultElement.details.symbol} + ${HELIUM_4_ION_2.details.symbol}",
                )
            }
        }
    }

    /**
     * Доля попыток (p,X), на которые реакция вообще срабатывает в этом тике. Дефолт 1.0.
     * Используется для bottleneck (¹⁴N(p,γ)¹⁵O реально на ~1000× медленнее остальных шагов
     * CNO-I) и slowdown (¹⁶O(p,γ)¹⁷F).
     */
    private fun captureRate(target: Element): Float = when (target) {
        NITROGEN_14_ION_7 -> 0.02f  // CNO-I bottleneck — сжато до x50 от реальных ~1000×
        OXYGEN_16_ION_8   -> 0.10f  // CNO-II slowdown
        else              -> 1.0f
    }

    /**
     * Веса (γ-канал, α-канал) для roulette-wheel branching. Дефолт (1, 1) — равновероятно,
     * если оба канала установлены. Конкретные значения — физические branching ratios
     * (сжатые для играбельности — реальные 0.04%/1%/5% доводим до 10% утечек).
     */
    private fun branchingWeights(target: Element): Pair<Float, Float> = when (target) {
        // CNO утечки: 10% (p,γ) уходит в следующий цикл, 90% (p,α) замыкает текущий.
        NITROGEN_15_ION_7   -> 0.1f to 0.9f  // CNO-I → CNO-II leak vs CNO-I closure
        OXYGEN_17_ION_8     -> 0.1f to 0.9f  // CNO-II → CNO-III leak vs CNO-II closure
        OXYGEN_18_ION_8     -> 0.1f to 0.9f  // CNO-III → CNO-IV leak vs CNO-III closure
        // NeNa/MgAl inter-cycle leaks: ²³Na+p в реальности почти 100% даёт ²⁰Ne+α; редкая
        // утечка в ²⁴Mg+γ — мост в MgAl. У нас 0.01% — заметно реже CNO.
        SODIUM_23_ION_11    -> 0.0001f to 1.0f
        // MgAl → Si: ²⁷Al+p в реальности тоже преимущественно (p,α), но γ-выход побольше.
        ALUMINUM_27_ION_13  -> 0.01f to 1.0f
        else                -> 1f to 1f
    }
}