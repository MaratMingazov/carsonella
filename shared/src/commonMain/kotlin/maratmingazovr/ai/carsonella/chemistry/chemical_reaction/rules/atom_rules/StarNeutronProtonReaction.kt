package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Element.NEUTRON
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.SubAtom
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.randomDirection

/**
 * (n,p) — захват нейтрона с испусканием протона:
 *
 *   A + n → A′ + p   (Z → Z−1, A неизменно)
 *
 * Изобарный переход вниз по Z. Зеркало [StarNeutronGammaReaction]: тот после захвата
 * сбрасывает γ и остаётся на том же Z (A→A+1), этот выбивает протон и уходит на Z−1.
 *
 * Канонический пример — **¹⁴N(n,p)¹⁴C**: именно так в атмосфере рождается радиоуглерод
 * (нейтроны от космических лучей бьют по азоту), а в звёздах это заметный «нейтронный яд».
 * Продукт ¹⁴C β⁻-нестабилен (¹⁴C → ¹⁴N + e⁻), так что реакция вместе с [BetaMinusDecay]
 * замыкает петлю ¹⁴N(n,p)¹⁴C(β⁻)¹⁴N — нейтрон в итоге «съедается», азот восстанавливается.
 *
 * **Нет кулоновского барьера на входе** (нейтрон нейтрален), поэтому (n,p) идёт и при
 * умеренных T. Пока ограничено TemperatureMode.Star для согласованности с (n,γ)/(α,n).
 *
 * Электронный баланс тривиален: и target, и продукт — голые ядра (e=0), вылетающий протон
 * тоже без электрона. Заряд сходится: A^Z⁺ + n → A′^(Z−1)⁺ + p⁺.
 *
 * Generic-правило: триггерится по полю Details.neutronProtonResult.
 */
class StarNeutronProtonReaction(
    private val entityGenerator: IEntityGenerator,
) : AtomReactionRule() {
    override val id = "StarNeutronProtonReaction"

    /** [atom1Element]/[atom2Element] выяснены в matchesAtoms — produce не вычисляет заново. */
    private data class Match(
        val atom1: Entity,
        val atom2: Entity,
        val atom1Element: Element,
        val atom2Element: Element,
    ) : MatchedData

    override fun matchesAtoms(reagents: List<Entity>): MatchedData? {
        if (reagents.size < 2) return null
        val firstAtom = reagents.first() as? Atom ?: return null
        val firstAtomPosition = firstAtom.state().value.kinematics.position
        if (!firstAtom.state().value.alive) return null
        val firstAtomElement = firstAtom.element
        if (firstAtomElement.details.neutronProtonResult == null) return null

        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filter {
                it is SubAtom && it.element == NEUTRON
            }
            .filter { it.state().value.alive }
            .map { it to it.state().value.kinematics.position.distanceSquareTo(firstAtomPosition) }
            .minByOrNull { it.second }
            ?: return null

        if (firstAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return null
        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return null
        if (distanceSquare >= firstAtomElement.details.radius * NEUTRON.details.radius * 2f) return null

        return Match(firstAtom, secondAtom, firstAtomElement, NEUTRON)   // второй реагент — нейтрон по фильтру
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (atom1, atom2, atom1Element, atom2Element) = match as Match
        val (direction, velocity) = calculateNewEntityDirectionAndVelocity(atom1, atom2)
        val resultPosition = atom1.state().value.kinematics.position
        val resultElement = atom1Element.details.neutronProtonResult!!
        // Перенос электронной оболочки на продукт (2C2): (n,p) понижает Z на 1 → если родитель почти
        // нейтрален, лишний электрон не помещается на продукт и улетает свободным e⁻ (shake-off).
        val parentElectrons = atom1.state().value.electrons
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
            // Протон-отдача вылетает по направлению движения СМ — отдельный degree of
            // freedom импульса между продуктами в проекте не моделируется (см. StarPPChain).
            entityGenerator.createEntity(
                Proton,
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

        val electronTail = if (shakeOff > 0) " + $shakeOff${ELECTRON.details.symbol}" else ""
        return ReactionOutcome(
            consumed = listOf(atom1, atom2),
            spawn = spawnList,
            description = "$id: ${atom1Element.symbol(parentElectrons)} + ${atom2Element.symbol(atom2.state().value.electrons)} → ${
                resultElement.symbol(
                    resultElectrons
                )
            } + ${Proton.details.symbol}$electronTail",
        )
    }
}