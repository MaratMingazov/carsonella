package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4
import maratmingazovr.ai.carsonella.chemistry.Element.NEUTRON
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.SubAtom
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.randomDirection

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
) : AtomReactionRule() {
    override val id = "StarNeutronAlphaReaction"

    /** [atom1Element]/[atom2Element] выяснены в matchesAtom — produce не вычисляет заново. */
    private data class Match(
        val atom1: Entity,
        val atom2: Entity,
        val atom1Element: Element,
        val atom2Element: Element,
    ) : MatchedData

    override fun matchesAtom(atom: Atom, neighbors: List<Entity>): MatchedData? {
        if (neighbors.isEmpty()) return null
        val atomPosition = atom.state().value.kinematics.position
        val atomElement = atom.element
        if (atomElement.details.neutronAlphaResult == null) return null

        val (secondAtom, distanceSquare) = neighbors
            .filter {
                it is SubAtom && it.element == NEUTRON
            }
            .filter { it.state().value.alive }
            .map { it to it.state().value.kinematics.position.distanceSquareTo(atomPosition) }
            .minByOrNull { it.second }
            ?: return null

        if (atom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return null
        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return null
        if (distanceSquare >= atomElement.details.radius * NEUTRON.details.radius * 2f) return null

        return Match(atom, secondAtom, atomElement, NEUTRON)   // второй реагент — нейтрон по фильтру
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (atom1, atom2, atom1Element, atom2Element) = match as Match
        val (direction, velocity) = calculateNewEntityDirectionAndVelocity(atom1, atom2)
        val resultPosition = atom1.state().value.kinematics.position
        val resultElement = atom1Element.details.neutronAlphaResult!!
        // Перенос электронной оболочки на продукт (2C2): (n,α) понижает Z на 2 → лишние электроны
        // (если родитель близок к нейтральному) не помещаются на продукт и улетают свободными e⁻ (shake-off).
        val parentElectrons = atom1.electrons
        val resultElectrons = minOf(parentElectrons, resultElement.details.p)
        val shakeOff = parentElectrons - resultElectrons

        val spawnList = mutableListOf<() -> Entity>()
        spawnList += {
            entityGenerator.createEntity(
                resultElement,
                resultPosition,
                direction,
                velocity,
                energy = 0f,
                atom1.getEnvironment(),
                electrons = resultElectrons,
            )
        }
        spawnList += {
            // α-отдача вылетает по направлению движения СМ — отдельный degree of
            // freedom импульса между продуктами в проекте не моделируется (см. StarPPChain).
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
                environment = atom1.getEnvironment(),
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
                    environment = atom1.getEnvironment(),
                    electrons = 1,
                )
            }
        }

        return ReactionOutcome(
            consumed = listOf(atom1, atom2),
            spawn = spawnList,
        )
    }
}