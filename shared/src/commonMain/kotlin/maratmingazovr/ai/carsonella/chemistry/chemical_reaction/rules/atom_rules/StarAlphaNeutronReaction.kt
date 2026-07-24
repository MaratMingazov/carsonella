package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome

/**
 * (α,n) — α-нейтронная реакция в недрах звезды. Ядро ловит ⁴He, выбрасывает нейтрон:
 *
 *   A + ⁴He → A′ + n   (Z → Z+2, A → A+3)
 *
 * Главный нейтронный источник для s-процесса (медленного захвата нейтронов на тяжёлых ядрах):
 *  · ²²Ne(α,n)²⁵Mg — «weak» s-process в массивных звёздах при He-burning (T ~ 3·10⁸ K).
 *  · ¹³C(α,n)¹⁶O — «main» s-process в AGB звёздах (T ~ 10⁸ K). У нас target ¹³C пока не реализован.
 *  · ¹⁸O(α,n)²¹Ne, ²⁵Mg(α,n)²⁸Si — вторичные источники.
 *
 * Generic-правило: триггерится по полю Details.alphaNeutronResult. Работает только в
 * TemperatureMode.Star — реакция требует T > 10⁸ K, недостижимых вне звезды.
 *
 * На некоторых target-ядрах (α,n) конкурирует с (α,γ) — например, ¹⁸O и ²²Ne идут по обоим
 * каналам. Реальное соотношение зависит от температуры (при низкой T доминирует (α,γ), при
 * высокой — (α,n)). В нашей модели все weight() = 0f, выбор канала случайный — это упрощение,
 * но оба пути наблюдаемы.
 */
class StarAlphaNeutronReaction(
    private val entityGenerator: IEntityGenerator,
) : AtomReactionRule() {
    override val id = "StarAlphaNeutronReaction"

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
        if (firstAtomElement.details.alphaNeutronResult == null) return false

        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filter {
                val sp = it.state().value.species
                sp is Species.Elemental && sp.element == HELIUM_4
            }
            .filter { it.state().value.alive }
            .map { it to it.state().value.position.distanceSquareTo(firstAtomPosition) }
            .minByOrNull { it.second }
            ?: return false

        if (firstAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false
        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false
        val secondSpecies = secondAtom.state().value.species
        if (secondSpecies !is Species.Elemental) return false
        val secondAtomElement = secondSpecies.element

        return if (distanceSquare < firstAtomElement.details.radius * secondAtomElement.details.radius * 2f) {
            atom1 = firstAtom
            atom2 = secondAtom
            atom1El = firstAtomElement
            atom2El = secondAtomElement
            true
        } else {
            false
        }
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {
        val a1 = atom1!!
        val a2 = atom2!!
        val (direction, velocity) = calculateNewEntityDirectionAndVelocity(a1, a2)
        val resultPosition = a1.state().value.position
        val atom1Element = atom1El!!   // запомнили в matchesAtoms
        val atom2Element = atom2El!!
        val resultElement = atom1Element.details.alphaNeutronResult!!
        // Перенос электронной оболочки на продукт (2C2): (α,n) повышает Z → кламп no-op, shake-off не нужен.
        val resultElectrons = minOf(a1.state().value.electrons, resultElement.details.p)

        return ReactionOutcome(
            consumed = listOf(a1, a2),
            spawn = listOf(
                {
                    entityGenerator.createEntity(
                        resultElement,
                        resultPosition,
                        direction,
                        velocity,
                        energy = 0f,
                        a1.getEnvironment(),
                        electrons = resultElectrons,
                    )
                },
                {
                    // Нейтрон-отдача вылетает по направлению движения СМ (impulse-splitting в проекте
                    // не моделируется — см. StarPPChain / AlphaProtonReaction).
                    entityGenerator.createEntity(
                        Element.NEUTRON,
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
                },
            ),
            description = "$id: ${atom1Element.symbol(a1.state().value.electrons)} + ${atom2Element.symbol(a2.state().value.electrons)} → ${
                resultElement.symbol(
                    resultElectrons
                )
            } + ${Element.NEUTRON.details.symbol}",
        )
    }
}