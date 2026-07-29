package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.randomDirection

/**
 * β⁻-распад (electron emission): нейтрон-избыточное ядро превращает один нейтрон в протон,
 * выбрасывая электрон и (в реальности) антинейтрино. Антинейтрино в модели опускаем.
 *
 *   n → p + e⁻ + ν̄ₑ
 *   Z → Z+1, N → N−1, A не меняется
 *
 * Зеркало [BetaPlusDecay]: тот двигает ядро вниз по Z, этот — вверх. Именно β⁻ толкает
 * s-процесс вверх по таблице: нейтрон-избыточный продукт (n,γ) распадается в следующий элемент.
 * Первый пример в проекте: ³¹Si → ³¹P + e⁻ (после ³⁰Si(n,γ)³¹Si) — перешагиваем с Si (Z=14) на P (Z=15).
 *
 * Generic-правило: триггерится по полю Details.betaMinusDecayResult. Если на элементе
 * прописано — он β⁻-нестабилен, реакция применима в любой среде. Срабатывание вероятностное —
 * каждый тик 2% шанс распасться (как у β⁺). Реальные t½ (³¹Si ≈ 2.6 ч) сильно сжаты для зрелищности.
 */
class BetaMinusDecay(
    private val entityGenerator: IEntityGenerator,
) : AtomReactionRule() {
    override val id = "BetaMinusDecay"

    /** [parentElement] выяснен в matchesAtoms — produce не вычисляет заново. */
    private data class Match(val parent: Entity, val parentElement: Element) : MatchedData

    override fun matchesAtoms(reagents: List<Entity>): MatchedData? {
        if (reagents.size != 1) return null
        val first = reagents.first()
        if (!first.state().value.alive) return null
        // species в локальный val → smart-cast к Elemental ниже (через Entity компилятор сам этого не знает).
        val species = first.state().value.species
        if (species !is Species.Atomic) return null
        val element = species.element
        if (element.details.betaMinusDecayResult == null) return null

        if (!chance(0.02f, entityGenerator.random)) return null

        return Match(first, element)
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (parent, parentElement) = match as Match
        val childElement = parentElement.details.betaMinusDecayResult!!
        val parentPosition = parent.state().value.position
        val parentRadius = parentElement.details.radius
        // Перенос оболочки на продукт (2C2): β⁻ повышает Z на 1 (n→p) → электроны помещаются, кламп no-op.
        // Вылетающий e⁻ — продукт распада ядра, а не shake-off оболочки.
        val childElectrons = minOf(parent.state().value.electrons, childElement.details.p)

        return ReactionOutcome(
            consumed = listOf(parent),
            spawn = listOf(
                {
                    entityGenerator.createEntity(
                        childElement,
                        parentPosition,
                        parent.state().value.direction,
                        parent.state().value.velocity,
                        energy = 0f,
                        environment = parent.getEnvironment(),
                        electrons = childElectrons,
                    )
                },
                {
                    entityGenerator.createEntity(
                        ELECTRON,
                        Position(parentPosition.x + parentRadius, parentPosition.y),
                        randomDirection(entityGenerator.random),
                        20f,
                        energy = 0f,
                        environment = parent.getEnvironment(),
                        electrons = 1,
                    )
                },
            ),
            description = "$id: ${parentElement.symbol(parent.state().value.electrons)} → ${
                childElement.symbol(
                    childElectrons
                )
            } + ${ELECTRON.details.symbol}",
        )
    }
}