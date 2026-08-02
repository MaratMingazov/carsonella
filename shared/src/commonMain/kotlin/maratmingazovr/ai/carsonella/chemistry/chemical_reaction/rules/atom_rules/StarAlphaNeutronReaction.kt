package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
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
        val firstAtomPosition = firstAtom.state().value.centerPosition
        if (!firstAtom.state().value.alive) return null
        val firstAtomElement = firstAtom.element
        if (firstAtomElement.details.alphaNeutronResult == null) return null

        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filterIsInstance<Atom>()
            .filter { it.element == HELIUM_4 }
            .filter { it.state().value.alive }
            .map { it to it.state().value.centerPosition.distanceSquareTo(firstAtomPosition) }
            .minByOrNull { it.second }
            ?: return null

        if (firstAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return null
        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return null
        val secondAtomElement = secondAtom.element

        return if (distanceSquare < firstAtomElement.details.radius * secondAtomElement.details.radius * 2f) {
            Match(firstAtom, secondAtom, firstAtomElement, secondAtomElement)
        } else {
            null
        }
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (atom1, atom2, atom1Element, atom2Element) = match as Match
        val (direction, velocity) = calculateNewEntityDirectionAndVelocity(atom1, atom2)
        val resultPosition = atom1.state().value.centerPosition
        val resultElement = atom1Element.details.alphaNeutronResult!!
        // Перенос электронной оболочки на продукт (2C2): (α,n) повышает Z → кламп no-op, shake-off не нужен.
        val resultElectrons = minOf(atom1.state().value.electrons, resultElement.details.p)

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
                        environment = atom1.getEnvironment(),
                        electrons = 0,
                    )
                },
            ),
            description = "$id: ${atom1Element.symbol(atom1.state().value.electrons)} + ${atom2Element.symbol(atom2.state().value.electrons)} → ${
                resultElement.symbol(
                    resultElectrons
                )
            } + ${Element.NEUTRON.details.symbol}",
        )
    }
}