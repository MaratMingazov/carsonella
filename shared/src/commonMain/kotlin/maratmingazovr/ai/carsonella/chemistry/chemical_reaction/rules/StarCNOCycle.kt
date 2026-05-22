package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.CARBON_12_ION_6
import maratmingazovr.ai.carsonella.chemistry.Element.CARBON_13_ION_6
import maratmingazovr.ai.carsonella.chemistry.Element.FLUORINE_17_ION_9
import maratmingazovr.ai.carsonella.chemistry.Element.FLUORINE_18_ION_9
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4_ION_2
import maratmingazovr.ai.carsonella.chemistry.Element.NITROGEN_13_ION_7
import maratmingazovr.ai.carsonella.chemistry.Element.NITROGEN_14_ION_7
import maratmingazovr.ai.carsonella.chemistry.Element.NITROGEN_15_ION_7
import maratmingazovr.ai.carsonella.chemistry.Element.OXYGEN_15_ION_8
import maratmingazovr.ai.carsonella.chemistry.Element.OXYGEN_16_ION_8
import maratmingazovr.ai.carsonella.chemistry.Element.OXYGEN_17_ION_8
import maratmingazovr.ai.carsonella.chemistry.Element.OXYGEN_18_ION_8
import maratmingazovr.ai.carsonella.chemistry.Element.PHOTON
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator

/**
 * CNO-циклы (Бете, 1938) — горение водорода в горячих звёздах с участием C/N/O как катализаторов.
 * Чистый эффект каждого цикла — тот же, что у pp-цепочки: 4p → ⁴He, но катализатор сохраняется.
 *
 * Этот класс реализует **CNO-I**, **CNO-II** и **CNO-III**. β⁺-распады между шагами
 * (¹³N→¹³C, ¹⁵O→¹⁵N, ¹⁷F→¹⁷O, ¹⁸F→¹⁸O) автоматически отрабатываются через generic
 * `BetaPlusDecay` — data-driven по `Details.betaPlusDecayResult`.
 *
 * **CNO-I** (CN-cycle):
 *   ¹²C + p → ¹³N + γ
 *   ¹³C + p → ¹⁴N + γ
 *   ¹⁴N + p → ¹⁵O + γ      ← bottleneck (реальное сечение ~1000x меньше других шагов, у нас сжато до x50)
 *   ¹⁵N + p → ¹²C + ⁴He    (замыкание на старт, ~99.96% в реальности)
 *
 * **CNO-II** (NO-cycle) — открывается через редкую утечку из CNO-I:
 *   ¹⁵N + p → ¹⁶O + γ      (~0.04% в реальности; у нас сжато до 10% для играбельности)
 *   ¹⁶O + p → ¹⁷F + γ      (медленный шаг; у нас дополнительно прижат `chance(0.1)`)
 *   ¹⁷F → ¹⁷O + e⁺         (β⁺, отдельное правило)
 *   ¹⁷O + p → ¹⁴N + ⁴He    (замыкание на CNO-I, ~99% в реальности)
 *
 * **CNO-III** — открывается через редкую утечку из CNO-II:
 *   ¹⁷O + p → ¹⁸F + γ      (~1% в реальности; у нас сжато до 10% для играбельности)
 *   ¹⁸F → ¹⁸O + e⁺         (β⁺, отдельное правило)
 *   ¹⁸O + p → ¹⁵N + ⁴He    (замыкание на CNO-I/II через ¹⁵N)
 *
 * Конфликты в резолвере (все разруливаются равными `weight()=0f`, случайным выбором):
 *  - На ¹²C: α-захват (`StarAlphaReaction`, → ¹⁶O) vs p-захват (CNO-I шаг 1, → ¹³N).
 *  - На ¹⁵N: α-захват (`StarAlphaReaction`, → ¹⁹F) vs p-захват (CNO-I/II финал, → ¹²C+α или ¹⁶O).
 *  - На ¹⁶O: α-захват (`StarAlphaReaction`, → ²⁰Ne) vs p-захват (CNO-II шаг 2, → ¹⁷F).
 *  - На ¹⁵N+p — внутренний branching между CNO-I и CNO-II через `chance(0.1)`.
 *  - На ¹⁷O+p — внутренний branching между CNO-II замыканием и CNO-III утечкой через `chance(0.1)`.
 */
class StarCNOCycle(
    private val entityGenerator: IEntityGenerator,
) : ReactionRule {

    override val id = "StarCNOCycle"

    private var atom1: Entity<*>? = null
    private var atom2: Entity<*>? = null
    private var resultElement: Element? = null
    private var extraElements: List<Element> = emptyList()

    override fun matches(reagents: List<Entity<*>>): Boolean {
        atom1 = null
        atom2 = null
        resultElement = null
        extraElements = emptyList()
        if (reagents.size < 2) return false
        val firstAtom = reagents.first()
        val firstAtomPosition = firstAtom.state().value.position
        val firstAtomElement = firstAtom.state().value.element
        if (!firstAtom.state().value.alive) return false
        if (firstAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false

        // В зависимости от катализатора определяем продукт. Второй реагент во всех шагах — протон.
        val (secondElement, result, extras) = when (firstAtomElement) {
            CARBON_12_ION_6   -> Triple(Proton, NITROGEN_13_ION_7, emptyList<Element>())
            CARBON_13_ION_6   -> Triple(Proton, NITROGEN_14_ION_7, emptyList())
            NITROGEN_14_ION_7 -> Triple(Proton, OXYGEN_15_ION_8,   emptyList())
            // ¹⁵N+p имеет два канала. В реальности ~99.96% идёт в CNO-I (¹²C+α),
            // ~0.04% — утечка в CNO-II (¹⁶O+γ). Сжимаем до 10% для играбельности.
            NITROGEN_15_ION_7 -> if (chance(0.1f, entityGenerator.random)) {
                Triple(Proton, OXYGEN_16_ION_8,  emptyList())                  // CNO-II старт
            } else {
                Triple(Proton, CARBON_12_ION_6,  listOf(HELIUM_4_ION_2))       // CNO-I замыкание
            }
            OXYGEN_16_ION_8   -> Triple(Proton, FLUORINE_17_ION_9, emptyList()) // CNO-II шаг 2
            // ¹⁷O+p имеет два канала. В реальности ~99% идёт в CNO-II замыкание (¹⁴N+α),
            // ~1% — утечка в CNO-III (¹⁸F+γ). Сжимаем до 10% для играбельности, симметрично ¹⁵N+p.
            OXYGEN_17_ION_8   -> if (chance(0.1f, entityGenerator.random)) {
                Triple(Proton, FLUORINE_18_ION_9, emptyList())                  // CNO-III старт
            } else {
                Triple(Proton, NITROGEN_14_ION_7, listOf(HELIUM_4_ION_2))       // CNO-II замыкание
            }
            OXYGEN_18_ION_8   -> Triple(Proton, NITROGEN_15_ION_7, listOf(HELIUM_4_ION_2)) // CNO-III замыкание
            else -> return false
        }

        // Ищем ближайший подходящий протон
        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filter { it.state().value.element == secondElement }
            .filter { it.state().value.alive }
            .map { it to it.state().value.position.distanceSquareTo(firstAtomPosition) }
            .minByOrNull { it.second }
            ?: return false

        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false

        if (distanceSquare >= firstAtomElement.details.radius * secondElement.details.radius * 2f) return false

        // ¹⁴N+p — bottleneck шаг CNO-I. В реальной физике сечение этой реакции ~1000x меньше остальных шагов цикла,
        // из-за чего катализатор большую часть времени проводит именно как ¹⁴N. У нас сжато до x50 через chance.
        if (firstAtomElement == NITROGEN_14_ION_7 && !chance(0.02f, entityGenerator.random)) return false
        // ¹⁶O+p — медленный шаг CNO-II (реально в ~3000x медленнее ¹⁵N+p). Дополнительно прижимаем для играбельности,
        // чтобы CNO-II не крутился быстрее CNO-I после того, как утечка уже произошла.
        if (firstAtomElement == OXYGEN_16_ION_8 && !chance(0.1f, entityGenerator.random)) return false

        atom1 = firstAtom
        atom2 = secondAtom
        resultElement = result
        extraElements = extras
        return true
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {
        val a1 = atom1!!
        val a2 = atom2!!
        val result = resultElement!!
        val extras = extraElements

        val (direction, velocity) = calculateNewEntityDirectionAndVelocity(a1, a2)
        val resultPosition = a1.state().value.position
        val resultRadius = result.details.radius
        val atom1Element = a1.state().value.element
        val atom2Element = a2.state().value.element
        val spawnList = mutableListOf<() -> Entity<*>>()

        spawnList += {
            entityGenerator.createEntity(
                result,
                resultPosition,
                direction,
                velocity,
                energy = a1.state().value.energy + a2.state().value.energy,
                a1.getEnvironment(),
            )
        }

        // Дополнительные продукты (для шага ¹⁵N+p → ¹²C + ⁴He)
        extras.forEachIndexed { index, extra ->
            val offsetSign = if (index % 2 == 0) 1f else -1f
            spawnList += {
                entityGenerator.createEntity(
                    extra,
                    Position(resultPosition.x + offsetSign * 1.5f * direction.x * resultRadius, resultPosition.y),
                    direction,
                    velocity,
                    energy = 0f,
                )
            }
        }

        // На каждом шаге CNO выделяется γ (физически от 1.9 до 7.5 МэВ; в проекте условные 1000 эВ как у pp-chain)
        val resultPhotonEnergy = 1000f
        spawnList += {
            entityGenerator.createEntity(
                PHOTON,
                Position(
                    resultPosition.x + 1.5f * direction.x * resultRadius,
                    resultPosition.y + 1.5f * direction.y * resultRadius,
                ),
                direction,
                10f,
                energy = resultPhotonEnergy,
            )
        }

        return ReactionOutcome(
            consumed = listOf(a1, a2),
            spawn = spawnList,
            description = "$id: ${atom1Element.details.symbol} + ${atom2Element.details.symbol} -> ${result.details.symbol} + ${PHOTON.details.symbol} [$resultPhotonEnergy ev]",
        )
    }
}