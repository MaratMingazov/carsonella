package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule
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
) : ReactionRule {
    override val id = "Luminescence"

    private var entity : Entity<*>? = null


    override suspend fun matches(reagents: List<Entity<*>>) : Boolean {
        entity = null

        if (reagents.size != 1) return false
        val first = reagents.first()
        if (!first.state().value.alive) return false

        val firstElement = first.state().value.element
        if (firstElement.energyLevels.isEmpty()) return false
        if (first.state().value.energy == 0f) return false
        if (!firstElement.energyLevels.contains(first.state().value.energy)) { throw Exception("SpontaneousEmission")}

        if (!chance(0.02f)) return false // в этом случае он с определенной вероятностью избавится от этой энергии

        entity = first
        return true
    }

    override suspend fun weight() = 0f

    override suspend fun produce(): ReactionOutcome {

        // нужно вычислить сколько энергии должен отдать атом
        val entityEnergy = entity!!.state().value.energy
        val entityEnvironment = entity!!.state().value.environment
        val entityElement = entity!!.state().value.element
        val index = entityElement.energyLevels.indexOf(entityEnergy)
        if (index < 0) throw Exception("SpontaneousEmission out of index")

        // электрон в атоме спустится на 1 уровень ниже и отдаст энергию
        val energyToExpose =
            if (index == 0) { entityEnergy }
            else { entityEnergy - entityElement.energyLevels[index - 1] }

        return ReactionOutcome(
            updateState = listOf { entity!!.addEnergy(-1 * energyToExpose) },
            spawn = listOf {
                entityGenerator.createEntity(
                    Element.Photon,
                    entity!!.state().value.position.plus(Position(Element.H.radius, 0f)),
                    randomDirection(),
                    40f,
                    energy = energyToExpose,
                    environment = entityEnvironment,
                )
            },
            description = "Люминесценция: ${entityElement.label} (${entityEnergy}eV) -> ${Element.Photon.label} (${energyToExpose}eV)",
        )
    }

}