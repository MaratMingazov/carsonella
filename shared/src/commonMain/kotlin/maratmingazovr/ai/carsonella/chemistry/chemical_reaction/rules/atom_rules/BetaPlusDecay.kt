package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Element.POSITRON
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.randomDirection

/**
 * β⁺-распад (positron emission): протон-избыточное ядро превращает один протон в нейтрон,
 * выбрасывая позитрон и (в реальности) нейтрино. Нейтрино в модели опускаем.
 *
 *   p → n + e⁺ + νₑ
 *   Z → Z−1, N → N+1, A не меняется
 *
 * Примеры в проекте: ¹³N → ¹³C + e⁺, ¹⁵O → ¹⁵N + e⁺ (промежуточные шаги CNO-цикла).
 *
 * Generic-правило: триггерится по полю Details.betaPlusDecayResult. Если на элементе
 * прописано — он β⁺-нестабилен, реакция применима в любой среде (не только в звезде).
 * Срабатывание вероятностное — каждый тик 2% шанс распасться, наблюдаемо за ~50 тиков (≈1 сек реального времени).
 * Реальные t½ в часах/минутах сильно сжаты для зрелищности — точные темпы по изотопам — отдельный TODO.
 */
class BetaPlusDecay(
    private val entityGenerator: IEntityGenerator,
) : AtomReactionRule() {
    override val id = "BetaPlusDecay"

    /** [parentElement] выяснен в matchesAtom — produce не вычисляет заново. */
    private data class Match(val parent: Atom, val parentElement: Element) : MatchedData

    override fun matchesAtom(atom: Atom, neighbors: List<Entity>): MatchedData? {
        if (neighbors.isNotEmpty()) return null
        val element = atom.element
        if (element.details.betaPlusDecayResult == null) return null

        if (!chance(0.02f, entityGenerator.random)) return null

        return Match(atom, element)
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (parent, parentElement) = match as Match
        val childElement = parentElement.details.betaPlusDecayResult!!
        val parentPosition = parent.kinematics.position
        val parentRadius = parentElement.details.radius
        // Перенос оболочки на продукт (2C2): β⁺ понижает Z на 1 (p→n) → если родитель почти нейтрален,
        // лишний электрон не помещается на продукт и улетает свободным e⁻ (shake-off). Вылетающий e⁺ —
        // продукт распада ядра.
        val parentElectrons = parent.electrons
        val childElectrons = minOf(parentElectrons, childElement.details.p)
        val shakeOff = parentElectrons - childElectrons

        val spawnList = mutableListOf<() -> Entity>()
        spawnList += {
            entityGenerator.createEntity(
                childElement,
                parentPosition,
                parent.kinematics.direction,
                parent.kinematics.velocity,
                energy = 0f,
                environment = parent.getEnvironment(),
                electrons = childElectrons,
            )
        }
        spawnList += {
            entityGenerator.createEntity(
                POSITRON,
                Position(parentPosition.x + parentRadius, parentPosition.y),
                randomDirection(entityGenerator.random),
                20f,
                energy = 0f,
                environment = parent.getEnvironment(),
                electrons = 0,
            )
        }
        repeat(shakeOff) {
            spawnList += {
                entityGenerator.createEntity(
                    ELECTRON,
                    Position(parentPosition.x - parentRadius, parentPosition.y),
                    randomDirection(entityGenerator.random),
                    20f,
                    energy = 0f,
                    environment = parent.getEnvironment(),
                    electrons = 1,
                )
            }
        }

        return ReactionOutcome(
            consumed = listOf(parent),
            spawn = spawnList,
        )
    }
}