package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.NEUTRON
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome

/**
 * (n,γ) — радиативный захват нейтрона ядром:
 *
 *   A + n → A′ + γ   (Z неизменно, A → A+1)
 *
 * Главный механизм **s-процесса** — медленного нейтронного захвата, через который во
 * Вселенной появляются почти все элементы тяжелее железа (Sr, Ba, Pb и др.). Каждый
 * захват увеличивает массовое число на единицу; когда продукт β⁻-нестабилен, он
 * распадается в изобар с Z+1, и цепочка продолжается выше по таблице.
 *
 * Эстетически интересная пара реакций — **цикл воспроизводства нейтронов** в AGB-звёздах:
 *   ¹²C(n,γ)¹³C  → ¹³C(α,n)¹⁶O  → выделяется новый нейтрон
 * То есть один нейтрон, попав на ¹²C, в итоге даёт назад нейтрон через ¹³C — а заодно
 * прокачивает углерод через C/N/O.
 *
 * **Нет кулоновского барьера**: нейтрон электрически нейтрален. Поэтому (n,γ) работает
 * даже при низких T — главное чтобы нейтроны вообще были рядом. В реальной физике в
 * молекулярных облаках свободные нейтроны быстро распадаются (β⁻, T½=14.8 мин), но в
 * звёздных недрах поток нейтронов от (α,n) и β⁻-распадов поддерживает s-процесс долго.
 *
 * Generic-правило: триггерится по полю Details.neutronGammaResult. Пока ограничено
 * TemperatureMode.Star для согласованности с другими ядерными правилами; можно ослабить.
 */
class StarNeutronGammaReaction(
    private val entityGenerator: IEntityGenerator,
) : AtomReactionRule() {
    override val id = "StarNeutronGammaReaction"

    /** [atom1Element]/[atom2Element] выяснены в matchesAtoms — produce не вычисляет заново. */
    private data class Match(
        val atom1: Entity,
        val atom2: Entity,
        val atom1Element: Element,
        val atom2Element: Element,
    ) : MatchedData

    override fun matchesAtoms(reagents: List<Entity>): MatchedData? {
        if (reagents.size < 2) return null
        val firstAtom = reagents.first()
        val firstAtomPosition = firstAtom.state().value.position
        if (!firstAtom.state().value.alive) return null
        // species в локальный val → smart-cast к Elemental ниже (через Entity компилятор сам этого не знает).
        val firstSpecies = firstAtom.state().value.species
        if (firstSpecies !is Species.Elemental) return null
        val firstAtomElement = firstSpecies.element
        if (firstAtomElement.details.neutronGammaResult == null) return null

        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filter {
                val sp = it.state().value.species
                sp is Species.Elemental && sp.element == NEUTRON
            }
            .filter { it.state().value.alive }
            .map { it to it.state().value.position.distanceSquareTo(firstAtomPosition) }
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
        val resultPosition = atom1.state().value.position
        val resultElement = atom1Element.details.neutronGammaResult!!
        // Перенос электронной оболочки на продукт (2C2): (n,γ) не меняет Z → кламп no-op, shake-off не нужен.
        val resultElectrons = minOf(atom1.state().value.electrons, resultElement.details.p)
        val resultPhotonEnergy = 1000f

        return ReactionOutcome(
            consumed = listOf(atom1, atom2),
            spawn = listOf(
                {
                    entityGenerator.createEntity(
                        resultElement,
                        resultPosition,
                        direction,
                        velocity,
                        energy = 0f,
                        atom1.getEnvironment(),
                        electrons = resultElectrons,
                    )
                },
                {
                    entityGenerator.createEntity(
                        Element.PHOTON,
                        Position(
                            resultPosition.x + 1.5f * direction.x * resultElement.details.radius,
                            resultPosition.y + 1.5f * direction.y * resultElement.details.radius,
                        ),
                        direction,
                        MAX_VELOCITY,
                        energy = resultPhotonEnergy,
                        environment = atom1.getEnvironment(),
                        electrons = 0,
                    )
                },
            ),
            description = "$id: ${atom1Element.symbol(atom1.state().value.electrons)} + ${atom2Element.symbol(atom2.state().value.electrons)} → ${
                resultElement.symbol(
                    resultElectrons
                )
            } + ${Element.PHOTON.details.symbol} [$resultPhotonEnergy ev]",
        )
    }
}