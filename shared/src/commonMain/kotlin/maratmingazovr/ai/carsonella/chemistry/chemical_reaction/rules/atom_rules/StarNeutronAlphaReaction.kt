package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4
import maratmingazovr.ai.carsonella.chemistry.Element.NEUTRON
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
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

    private var atom1: Entity<*>? = null
    private var atom2: Entity<*>? = null
    private var atom1El: Element? = null   // элементы атомов, запомненные в matchesAtoms — produce не вычисляет заново
    private var atom2El: Element? = null

    override fun matchesAtoms(reagents: List<Entity<*>>): Boolean {
        atom1 = null
        atom2 = null
        atom1El = null
        atom2El = null
        if (reagents.size < 2) return false
        val firstAtom = reagents.first()
        val firstAtomPosition = firstAtom.state().value.position
        if (!firstAtom.state().value.alive) return false
        // species в локальный val → smart-cast к Elemental ниже (через Entity<*> компилятор сам этого не знает).
        val firstSpecies = firstAtom.state().value.species
        if (firstSpecies !is Species.Elemental) return false
        val firstAtomElement = firstSpecies.element
        if (firstAtomElement.details.neutronAlphaResult == null) return false

        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filter {
                val sp = it.state().value.species
                sp is Species.Elemental && sp.element == NEUTRON
            }
            .filter { it.state().value.alive }
            .map { it to it.state().value.position.distanceSquareTo(firstAtomPosition) }
            .minByOrNull { it.second }
            ?: return false

        if (firstAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false
        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false
        if (distanceSquare >= firstAtomElement.details.radius * NEUTRON.details.radius * 2f) return false

        atom1 = firstAtom
        atom2 = secondAtom
        atom1El = firstAtomElement
        atom2El = NEUTRON   // второй реагент — нейтрон по фильтру
        return true
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {
        val a1 = atom1!!
        val a2 = atom2!!
        val (direction, velocity) = calculateNewEntityDirectionAndVelocity(a1, a2)
        val resultPosition = a1.state().value.position
        val atom1Element = atom1El!!   // запомнили в matchesAtoms
        val atom2Element = atom2El!!
        val resultElement = atom1Element.details.neutronAlphaResult!!
        // Перенос электронной оболочки на продукт (2C2): (n,α) понижает Z на 2 → лишние электроны
        // (если родитель близок к нейтральному) не помещаются на продукт и улетают свободными e⁻ (shake-off).
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
            description = "$id: ${atom1Element.symbol(parentElectrons)} + ${atom2Element.symbol(a2.state().value.electrons)} → ${
                resultElement.symbol(
                    resultElectrons
                )
            } + ${HELIUM_4.symbol(0)}$electronTail",
        )
    }
}