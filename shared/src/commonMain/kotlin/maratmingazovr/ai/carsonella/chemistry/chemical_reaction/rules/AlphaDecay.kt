package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
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
) : ReactionRule {
    override val id = "AlphaDecay"

    private var entity: Entity<*>? = null

    override fun matches(reagents: List<Entity<*>>): Boolean {
        entity = null

        if (reagents.size != 1) return false
        val first = reagents.first()
        if (!first.state().value.alive) return false
        if (first.state().value.element.details.alphaDecayResult == null) return false

        if (!chance(0.02f, entityGenerator.random)) return false

        entity = first
        return true
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {
        val parent = entity!!
        val parentElement = parent.state().value.element
        val childElement = parentElement.details.alphaDecayResult!!
        val parentPosition = parent.state().value.position
        val parentRadius = parentElement.details.radius
        val childElectrons = minOf(parent.state().value.electrons, childElement.details.p)

        return ReactionOutcome(
            consumed = listOf(parent),
            spawn = listOf(
                {
                    entityGenerator.createEntity(
                        childElement,
                        parentPosition,
                        parent.state().value.direction,
                        parent.state().value.velocity,
                        energy = parent.state().value.energy,
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
            description = "$id: ${parentElement.symbol(parent.state().value.electrons)} → ${childElement.symbol(childElectrons)} + ${HELIUM_4.details.symbol}",
        )
    }
}