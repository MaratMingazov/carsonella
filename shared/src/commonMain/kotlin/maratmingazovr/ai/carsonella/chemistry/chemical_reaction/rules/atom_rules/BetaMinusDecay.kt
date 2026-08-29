package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.registry.AtomElement
import maratmingazovr.ai.carsonella.chemistry.registry.AtomElement.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Entity
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

    /** [parentElement] выяснен в matchesAtom — produce не вычисляет заново. */
    private data class Match(val parent: Atom, val parentElement: AtomElement) : MatchedData

    override fun matchesAtom(atom: Atom, neighbors: List<Entity>): MatchedData? {
        if (neighbors.isNotEmpty()) return null
        val element = atom.element
        if (element.details.betaMinusDecayResult == null) return null

        if (!chance(0.02f, entityGenerator.random)) return null

        return Match(atom, element)
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (parent, parentElement) = match as Match
        val childElement = parentElement.details.betaMinusDecayResult!!
        val parentPosition = parent.kinematics.position
        val parentRadius = parentElement.details.radius
        // Перенос оболочки на продукт (2C2): β⁻ повышает Z на 1 (n→p) → электроны помещаются, кламп no-op.
        // Вылетающий e⁻ — продукт распада ядра, а не shake-off оболочки.
        val childElectrons = minOf(parent.electrons, childElement.details.p)

        return ReactionOutcome(
            consumed = listOf(parent),
            spawn = listOf(
                {
                    entityGenerator.createAtom(
                        childElement,
                        parentPosition,
                        parent.kinematics.direction,
                        parent.kinematics.velocity,
                        energy = 0f,
                        environment = parent.getEnvironment(),
                        electrons = childElectrons,
                    )
                },
                {
                    entityGenerator.createAtom(
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
        )
    }
}