package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.randomDirection

/**
 * (α,p) — α-протонная реакция. Ядро ловит ⁴He и выбрасывает протон:
 *
 *   A + ⁴He → A′ + p   (Z → Z+1, A → A+3)
 *
 * Историческая первая искусственная трансмутация — ¹⁴N + α → ¹⁷O + p (Резерфорд, 1919).
 *
 * Generic-правило: триггерится по полю Details.alphaProtonResult. Работает только в
 * TemperatureMode.Space — это наш аналог «лабораторного» режима, где α из звёздного
 * outflow встречает холодные ядра. В Star-моде намеренно не включаем: одна star-мода без
 * различения H/He vs C/O/Si режимов → (α,p) над любой звёздной T over-fire-нула бы (α,γ).
 *
 * Реагент-α принимается в любой степени ионизации (⁴He²⁺ / ⁴He¹⁺ / ⁴He) — электронная
 * оболочка для ядерной реакции малозначима (эффект экранирования ~1-2%).
 *
 * Электронный баланс — гибрид:
 * - target-сторона (вариант A): электроны target остаются на продукте — каждая форма
 *   target имеет свой alphaProtonResult с тем же числом электронов (¹⁴N⁷⁺→¹⁷O⁸⁺, ¹⁴N⁶⁺→¹⁷O⁷⁺).
 * - α-сторона (вариант B): электроны α освобождаются как свободные e⁻ — потом сами идут
 *   на RecombinationReaction. Физически это shake-off ionization: ядерная (α,p) выделяет
 *   ~МэВ, что на порядки больше энергии связи электрона.
 */
class AlphaProtonReaction(
    private val entityGenerator: IEntityGenerator,
) : AtomReactionRule() {
    override val id = "AlphaProtonReaction"

    private var target: Entity? = null
    private var alpha: Entity? = null
    private var targetEl: Element? = null   // элементы реагентов, запомненные в matchesAtoms — produce не вычисляет заново
    private var alphaEl: Element? = null

    override fun matchesAtoms(reagents: List<Entity>): Boolean {
        target = null
        alpha = null
        targetEl = null
        alphaEl = null
        if (reagents.size < 2) return false

        val first = reagents.first()
        if (!first.state().value.alive) return false
        if (first.getEnvironment().getEnvTemperature() != TemperatureMode.Space) return false

        // species в локальный val → smart-cast к Elemental ниже (через Entity компилятор сам этого не знает).
        val firstSpecies = first.state().value.species
        if (firstSpecies !is Species.Elemental) return false
        val firstElement = firstSpecies.element
        if (firstElement.details.alphaProtonResult == null) return false

        val firstPosition = first.state().value.position
        val (alphaCandidate, distanceSquare) = reagents
            .drop(1)
            .filter { it.state().value.alive }
            .filter {
                val sp = it.state().value.species
                sp is Species.Elemental && sp.element == HELIUM_4
            }
            .filter { it.getEnvironment().getEnvTemperature() == TemperatureMode.Space }
            .map { it to it.state().value.position.distanceSquareTo(firstPosition) }
            .minByOrNull { it.second }
            ?: return false

        val alphaSpecies = alphaCandidate.state().value.species
        if (alphaSpecies !is Species.Elemental) return false
        val alphaElement = alphaSpecies.element
        val contactRadiusSquare = firstElement.details.radius * alphaElement.details.radius * 2f
        if (distanceSquare >= contactRadiusSquare) return false

        target = first
        alpha = alphaCandidate
        targetEl = firstElement
        alphaEl = alphaElement
        return true
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {
        val t = target!!
        val a = alpha!!
        val targetElement = targetEl!!   // запомнили в matchesAtoms
        val alphaElement = alphaEl!!
        val resultElement = targetElement.details.alphaProtonResult!!

        val (direction, velocity) = calculateNewEntityDirectionAndVelocity(t, a)
        val resultPosition = t.state().value.position
        val resultRadius = resultElement.details.radius
        // Перенос оболочки (2C2): электроны target остаются на продукте (Z+1 → кламп no-op),
        // а электроны α освобождаются как свободные e⁻ (shake-off α — существующая модель).
        val targetElectrons = minOf(t.state().value.electrons, resultElement.details.p)
        val freedAlphaElectrons = a.state().value.electrons

        val spawnList = mutableListOf<() -> Entity>()
        spawnList += {
            entityGenerator.createEntity(
                resultElement,
                resultPosition,
                direction,
                velocity,
                energy = t.state().value.energy + a.state().value.energy,
                environment = t.getEnvironment(),
                electrons = targetElectrons,
            )
        }
        spawnList += {
            // Протон-отдача вылетает по направлению движения СМ — отдельный degree of freedom
            // импульса между продуктами в проекте не моделируется (см. StarPPChain).
            entityGenerator.createEntity(
                Proton,
                Position(
                    resultPosition.x + 1.5f * direction.x * resultRadius,
                    resultPosition.y + 1.5f * direction.y * resultRadius,
                ),
                direction,
                20f,
                energy = 0f,
                environment = t.getEnvironment(),
                electrons = 0,
            )
        }
        repeat(freedAlphaElectrons) {
            spawnList += {
                entityGenerator.createEntity(
                    ELECTRON,
                    Position(resultPosition.x, resultPosition.y + resultRadius),
                    randomDirection(entityGenerator.random),
                    20f,
                    energy = 0f,
                    environment = t.getEnvironment(),
                    electrons = 1,
                )
            }
        }

        val electronTail = if (freedAlphaElectrons > 0) " + ${freedAlphaElectrons}${ELECTRON.details.symbol}" else ""
        return ReactionOutcome(
            consumed = listOf(t, a),
            spawn = spawnList,
            description = "$id: ${targetElement.symbol(t.state().value.electrons)} + ${alphaElement.symbol(a.state().value.electrons)} → ${
                resultElement.symbol(
                    targetElectrons
                )
            } + ${Proton.details.symbol}$electronTail",
        )
    }
}