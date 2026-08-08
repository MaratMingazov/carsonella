package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StateUpdate
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

    /** [entityElement] выяснен в matchesAtom — produce не вычисляет заново. */
    private data class Match(val entity: Entity, val entityElement: Element) : MatchedData

    override fun matchesAtom(atom: Atom, neighbors: List<Entity>): MatchedData? {
        if (neighbors.isNotEmpty()) return null

        val atomElement = atom.element
        val levels = atomElement.energyLevels(atom.electrons)
        if (levels.isEmpty()) return null
        if (atom.state().value.energy == 0f) return null
        if (!levels.contains(atom.state().value.energy)) { throw Exception("SpontaneousEmission")}

        if (!chance(0.02f, entityGenerator.random)) return null // в этом случае он с определенной вероятностью избавится от этой энергии

        return Match(atom, atomElement)
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (entity, entityElement) = match as Match

        // нужно вычислить сколько энергии должен отдать атом
        val entityEnergy = entity.state().value.energy
        val entityPosition = entity.state().value.kinematics.position
        val entityRadius = entityElement.details.radius
        val levels = entityElement.energyLevels(entity.electrons)
        val index = levels.indexOf(entityEnergy)
        if (index < 0) throw Exception("SpontaneousEmission out of index")

        // электрон в атоме спустится на 1 уровень ниже и отдаст энергию
        val targetEnergy = if (index == 0) 0f else levels[index - 1]
        val photonEnergy = entityEnergy - targetEnergy
        val photonVelocity = MAX_VELOCITY
        val photonDirection = randomDirection(entityGenerator.random)
        val photonOffset = entityRadius + Element.PHOTON.details.radius
        val photonPosition = entityPosition.addVelocity(photonDirection * photonOffset)

        return ReactionOutcome(
            // setEnergy(targetEnergy) вместо addEnergy(-energyToExpose) — записываем точное значение
            // уровня из таблицы, чтобы не накапливался float-дрейф и contains() не падал на следующем тике.
            updateState = listOf(StateUpdate(entity) { entity.setEnergy(targetEnergy) }),
            spawn = listOf {
                entityGenerator.createEntity(
                    Element.PHOTON,
                    photonPosition,
                    photonDirection,
                    photonVelocity,
                    energy = photonEnergy,
                    environment = entity.getEnvironment(),
                    electrons = 0,
                )
            },
        )
    }

}