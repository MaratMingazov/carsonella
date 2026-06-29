package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.randomDirection

/**
 * Когда электрон в атоме водорода падает с более высокого уровня на более низкий, он излучает фотон.
 * Это процесс называется спонтанное излучение (spontaneous emission).
 *
 * Люминесценция — это холодное свечение вещества, возникающее после поглощения им энергии возбуждения,
 * то есть излучение света нетеплового происхождения, в отличие от накала.
 * В результате перехода молекул из возбуждённого состояния в основное состояние происходит излучение света.
 * Этот процесс отличается от теплового излучения и может быть вызван различными видами энергии,
 * например, химическими реакциями, электрическим полем или облучением.
 *
 * Атом или молекула находится в возбужденном состоянии, он может испускать фотон, чтобы отдать лишнюю энергию
 */
class SpontaneousEmission(
    private val entityGenerator: IEntityGenerator,
) : AtomReactionRule() {
    override val id = "Luminescence"

    private var entity : Entity<*>? = null


    override fun matchesAtoms(reagents: List<Entity<*>>) : Boolean {
        entity = null

        if (reagents.size != 1) return false
        val first = reagents.first()
        if (!first.state().value.alive) return false

        val firstElement = first.state().value.element
        val levels = firstElement.energyLevels(first.state().value.electrons)
        if (levels.isEmpty()) return false
        if (first.state().value.energy == 0f) return false
        if (!levels.contains(first.state().value.energy)) { throw Exception("SpontaneousEmission")}

        if (!chance(0.02f, entityGenerator.random)) return false // в этом случае он с определенной вероятностью избавится от этой энергии

        entity = first
        return true
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {

        // нужно вычислить сколько энергии должен отдать атом
        val entityEnergy = entity!!.state().value.energy
        val entityElement = entity!!.state().value.element
        val levels = entityElement.energyLevels(entity!!.state().value.electrons)
        val index = levels.indexOf(entityEnergy)
        if (index < 0) throw Exception("SpontaneousEmission out of index")

        // электрон в атоме спустится на 1 уровень ниже и отдаст энергию
        val targetEnergy = if (index == 0) 0f else levels[index - 1]
        val energyToExpose = entityEnergy - targetEnergy

        return ReactionOutcome(
            // setEnergy(targetEnergy) вместо addEnergy(-energyToExpose) — записываем точное значение
            // уровня из таблицы, чтобы не накапливался float-дрейф и contains() не падал на следующем тике.
            updateState = listOf { entity!!.setEnergy(targetEnergy) },
            spawn = listOf {
                entityGenerator.createEntity(
                    Element.PHOTON,
                    entity!!.state().value.position.plus(Position(Element.HYDROGEN.details.radius, 0f)),
                    randomDirection(entityGenerator.random),
                    40f,
                    energy = energyToExpose,
                    environment = entity!!.getEnvironment(),
                    electrons = 0,
                )
            },
            description = "$id: ${entityElement.details.label} -> ${Element.PHOTON.details.symbol} [$energyToExpose ev]",
        )
    }

}