package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.randomDirection

/**
 * α-распад: ядро испускает ⁴He²⁺ (голое ядро — без электронов).
 *
 *   A(Z) → A′(Z-2) + ⁴He   (A′ = A-4, Z′ = Z-2)
 *
 * Замыкает свинцово-висмутовый цикл s-процесса: ²¹⁰Po → ²⁰⁶Pb + α,
 * после чего ²⁰⁶Pb возвращается в s-цепочку (n,γ)-захватами.
 *
 * Generic-правило: триггерится по полю Details.alphaDecayResult.
 * По образцу [BetaMinusDecay] — 2% шанс в тик, среда-независимо.
 */
class AlphaDecay(
    private val entityGenerator: IEntityGenerator,
) : AtomReactionRule() {
    override val id = "AlphaDecay"

    /** [parentElement] выяснен в matchesAtom — produce не вычисляет заново. */
    private data class Match(val parent: Atom, val parentElement: Element) : MatchedData

    override fun matchesAtom(atom: Atom, neighbors: List<Entity>): MatchedData? {
        if (neighbors.isNotEmpty()) return null
        val element = atom.element
        if (element.details.alphaDecayResult == null) return null

        if (!chance(0.02f, entityGenerator.random)) return null

        return Match(atom, element)
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (parent, parentElement) = match as Match
        val childElement = parentElement.details.alphaDecayResult!!
        val parentPosition = parent.kinematics.position
        val parentRadius = parentElement.details.radius
        val childElectrons = minOf(parent.electrons, childElement.details.p)

        return ReactionOutcome(
            consumed = listOf(parent),
            spawn = listOf(
                {
                    entityGenerator.createEntity(
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
                    // α-частица — голое ядро ⁴He²⁺ (electrons = 0)
                    entityGenerator.createEntity(
                        HELIUM_4,
                        Position(parentPosition.x + parentRadius, parentPosition.y),
                        randomDirection(entityGenerator.random),
                        20f,
                        energy = 0f,
                        environment = parent.getEnvironment(),
                        electrons = 0,
                    )
                },
            ),
        )
    }
}