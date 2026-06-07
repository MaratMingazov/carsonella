package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ALUMINUM_27
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4
import maratmingazovr.ai.carsonella.chemistry.Element.NEUTRON
import maratmingazovr.ai.carsonella.chemistry.Element.NITROGEN_14
import maratmingazovr.ai.carsonella.chemistry.Element.NITROGEN_15
import maratmingazovr.ai.carsonella.chemistry.Element.OXYGEN_16
import maratmingazovr.ai.carsonella.chemistry.Element.OXYGEN_17
import maratmingazovr.ai.carsonella.chemistry.Element.OXYGEN_18
import maratmingazovr.ai.carsonella.chemistry.Element.PHOTON
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Element.SODIUM_23
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.randomDirection

/**
 * Star proton capture — объединённое generic-правило для всех (p,X) реакций в звезде:
 *
 *   A + p → A′ + γ   (p,γ) — радиативный захват, Z→Z+1, A→A+1
 *   A + p → A′ + ⁴He (p,α) — выброс α, Z→Z-1, A→A-3
 *   A + p → A′ + n   (p,n) — выброс нейтрона, Z→Z+1, A→A (изобарный сосед)
 *
 * Покрывает все каталитические циклы горения водорода (CNO/NeNa/MgAl), pp-III,
 * hot CNO breakouts и (p,n) каналы s-процесса/spallation. Триггерится по полям
 * `protonGammaResult`/`protonAlphaResult`/`protonNeutronResult` в Details — структура
 * «что может произойти» хранится там; вероятности «с какой долей и скоростью»
 * захардкожены в этом файле (см. `captureRate` и `branchingWeights`).
 *
 * Алгоритм matches():
 *   1. Найти target + ближайший Proton, проверить контакт и TemperatureMode.Star.
 *   2. Применить `captureRate(target)` — bottleneck/slowdown для конкретных target-ядер.
 *      Если roll не прошёл, реакция в этом тике не идёт.
 *   3. Собрать список доступных исходов (γ/α/n) с весами из `branchingWeights(target)`.
 *      Roulette wheel: один roll выбирает один канал. Это математически корректно (в
 *      отличие от двух независимых правил, где случались "оба сработали" / "ни один").
 */
class StarProtonCaptureReaction(
    private val entityGenerator: IEntityGenerator,
) : ReactionRule {
    override val id = "StarProtonCaptureReaction"

    private sealed class Outcome {
        data class Gamma(val product: Element) : Outcome()
        data class Alpha(val product: Element) : Outcome()
        data class Neutron(val product: Element) : Outcome()
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
        val neutronResult = firstAtomElement.details.protonNeutronResult
        if (gammaResult == null && alphaResult == null && neutronResult == null) return false

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
        val (gW, aW, nW) = branchingWeights(firstAtomElement)
        val candidates = mutableListOf<Pair<Outcome, Float>>()
        if (gammaResult != null && gW > 0f) candidates += (Outcome.Gamma(gammaResult) as Outcome) to gW
        if (alphaResult != null && aW > 0f) candidates += (Outcome.Alpha(alphaResult) as Outcome) to aW
        if (neutronResult != null && nW > 0f) candidates += (Outcome.Neutron(neutronResult) as Outcome) to nW
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
        // Перенос электронной оболочки на продукт (2C2): продукт наследует электроны target-ядра,
        // но не больше своего Z. (p,γ)/(p,n) повышают Z → кламп no-op; (p,α) понижает Z → лишние
        // электроны улетают свободными e⁻ (shake-off). Захваченный протон голый, испущенная α — голая.
        val parentElectrons = a1.state().value.electrons

        return when (outcome) {
            is Outcome.Gamma -> {
                val resultElement = outcome.product
                val resultElectrons = minOf(parentElectrons, resultElement.details.p)
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
                                electrons = resultElectrons,
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
                    description = "$id (p,γ): ${atom1Element.symbol(parentElectrons)} + ${atom2Element.details.symbol} → ${resultElement.symbol(resultElectrons)} + ${PHOTON.details.symbol}",
                )
            }
            is Outcome.Alpha -> {
                val resultElement = outcome.product
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
                    // Испущенная α — голое ядро ⁴He²⁺ (electrons = 0).
                    entityGenerator.createEntity(
                        HELIUM_4,
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
                        )
                    }
                }
                val electronTail = if (shakeOff > 0) " + $shakeOff${ELECTRON.details.symbol}" else ""
                ReactionOutcome(
                    consumed = listOf(a1, a2),
                    spawn = spawnList,
                    description = "$id (p,α): ${atom1Element.symbol(parentElectrons)} + ${atom2Element.details.symbol} → ${resultElement.symbol(resultElectrons)} + ${HELIUM_4.symbol(0)}$electronTail",
                )
            }
            is Outcome.Neutron -> {
                val resultElement = outcome.product
                val resultElectrons = minOf(parentElectrons, resultElement.details.p)
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
                                electrons = resultElectrons,
                            )
                        },
                        {
                            // Нейтрон-отдача по направлению СМ (impulse-split не моделируется).
                            entityGenerator.createEntity(
                                NEUTRON,
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
                    description = "$id (p,n): ${atom1Element.symbol(parentElectrons)} + ${atom2Element.details.symbol} → ${resultElement.symbol(resultElectrons)} + ${NEUTRON.details.symbol}",
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
        NITROGEN_14 -> 0.02f  // CNO-I bottleneck — сжато до x50 от реальных ~1000×
        OXYGEN_16   -> 0.10f  // CNO-II slowdown
        else        -> 1.0f
    }

    /**
     * Веса (γ-канал, α-канал, n-канал) для roulette-wheel branching. Дефолт (1, 1, 1) —
     * равновероятно, если несколько каналов установлены. Конкретные значения — физические
     * branching ratios (сжатые для играбельности — реальные 0.04%/1%/5% доводим до 10%).
     * Если у target нет соответствующего result-поля, вес игнорируется.
     */
    private fun branchingWeights(target: Element): Triple<Float, Float, Float> = when (target) {
        // CNO утечки: 10% (p,γ) уходит в следующий цикл, 90% (p,α) замыкает текущий.
        NITROGEN_15   -> Triple(0.1f, 0.9f, 0f)  // CNO-I → CNO-II leak vs CNO-I closure
        OXYGEN_17     -> Triple(0.1f, 0.9f, 0f)  // CNO-II → CNO-III leak vs CNO-II closure
        OXYGEN_18     -> Triple(0.1f, 0.9f, 0f)  // CNO-III → CNO-IV leak vs CNO-III closure
        // NeNa/MgAl inter-cycle leaks: ²³Na+p в реальности почти 100% даёт ²⁰Ne+α; редкая
        // утечка в ²⁴Mg+γ — мост в MgAl. У нас 0.01% — заметно реже CNO.
        SODIUM_23     -> Triple(0.0001f, 1.0f, 0f)
        // MgAl → Si: ²⁷Al+p в реальности тоже преимущественно (p,α), но γ-выход побольше.
        ALUMINUM_27   -> Triple(0.01f, 1.0f, 0f)
        else                -> Triple(1f, 1f, 1f)
    }
}