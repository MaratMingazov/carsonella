package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.NEUTRON
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
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

    private var atom1: Entity? = null
    private var atom2: Entity? = null
    private var atom1El: Element? = null   // элементы атомов, запомненные в matchesAtoms — produce не вычисляет заново
    private var atom2El: Element? = null

    override fun matchesAtoms(reagents: List<Entity>): Boolean {
        atom1 = null
        atom2 = null
        atom1El = null
        atom2El = null
        if (reagents.size < 2) return false
        val firstAtom = reagents.first()
        val firstAtomPosition = firstAtom.state().value.position
        if (!firstAtom.state().value.alive) return false
        // species в локальный val → smart-cast к Elemental ниже (через Entity компилятор сам этого не знает).
        val firstSpecies = firstAtom.state().value.species
        if (firstSpecies !is Species.Elemental) return false
        val firstAtomElement = firstSpecies.element
        if (firstAtomElement.details.neutronGammaResult == null) return false

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
        val resultElement = atom1Element.details.neutronGammaResult!!
        // Перенос электронной оболочки на продукт (2C2): (n,γ) не меняет Z → кламп no-op, shake-off не нужен.
        val resultElectrons = minOf(a1.state().value.electrons, resultElement.details.p)
        val resultPhotonEnergy = 1000f

        return ReactionOutcome(
            consumed = listOf(a1, a2),
            spawn = listOf(
                {
                    entityGenerator.createEntity(
                        resultElement,
                        resultPosition,
                        direction,
                        velocity,
                        energy = a1.state().value.energy + a2.state().value.energy,
                        a1.getEnvironment(),
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
                        10f,
                        energy = resultPhotonEnergy,
                        environment = a1.getEnvironment(),
                        electrons = 0,
                    )
                },
            ),
            description = "$id: ${atom1Element.symbol(a1.state().value.electrons)} + ${atom2Element.symbol(a2.state().value.electrons)} → ${
                resultElement.symbol(
                    resultElectrons
                )
            } + ${Element.PHOTON.details.symbol} [$resultPhotonEnergy ev]",
        )
    }
}