package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.CARBON_12_ION_6
import maratmingazovr.ai.carsonella.chemistry.Element.CARBON_13_ION_6
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4_ION_2
import maratmingazovr.ai.carsonella.chemistry.Element.NITROGEN_13_ION_7
import maratmingazovr.ai.carsonella.chemistry.Element.NITROGEN_14_ION_7
import maratmingazovr.ai.carsonella.chemistry.Element.NITROGEN_15_ION_7
import maratmingazovr.ai.carsonella.chemistry.Element.OXYGEN_15_ION_8
import maratmingazovr.ai.carsonella.chemistry.Element.PHOTON
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator

/**
 * CNO-цикл (Бете, 1938) — горение водорода в горячих звёздах с участием C/N/O как катализаторов.
 * Чистый эффект — тот же, что у pp-цепочки: 4p → ⁴He. Но катализатор сохраняется.
 *
 * Четыре протон-захватных шага (этот класс):
 *   1: ¹²C + p → ¹³N⁷⁺ + γ
 *   2: ¹³C + p → ¹⁴N⁷⁺ + γ
 *   3: ¹⁴N + p → ¹⁵O⁸⁺ + γ        ← узкое горлышко цикла — сечение реакции в реальности в ~1000x меньше,
 *                                    чем у других шагов. У нас сжато до x50 через `chance(0.02)`,
 *                                    благодаря чему ¹⁴N задерживается в звезде заметно дольше других катализаторов.
 *   4: ¹⁵N + p → ¹²C⁶⁺ + ⁴He²⁺    (цикл замыкается)
 *
 * Два β⁺-распада между ними (¹³N→¹³C и ¹⁵O→¹⁵N) живут в отдельном `BetaPlusDecay` —
 * generic single-reagent правило, data-driven через `Details.betaPlusDecayResult`.
 *
 * Конфликт с `StarAlphaReaction` на ¹²C: ¹²C может либо захватить α (`alphaReactionResult = ¹⁶O`),
 * либо протон (CNO шаг 1) — оба правила matches() вернут true, резолвер выбирает случайно
 * (все weight() = 0f). Физически это естественная конкуренция двух процессов.
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
            NITROGEN_15_ION_7 -> Triple(Proton, CARBON_12_ION_6,   listOf(HELIUM_4_ION_2))
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

        // ¹⁴N+p — bottleneck шаг CNO. В реальной физике сечение этой реакции ~1000x меньше остальных шагов цикла,
        // из-за чего катализатор большую часть времени проводит именно как ¹⁴N. У нас сжато до x50 через chance.
        if (firstAtomElement == NITROGEN_14_ION_7 && !chance(0.02f, entityGenerator.random)) return false

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