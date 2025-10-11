package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.Electron
import maratmingazovr.ai.carsonella.chemistry.Element.Photon
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.randomDirection

/**
 * Фотоионизация — это процесс, при котором атом или молекула теряет электрон под воздействием фотона, становясь ионом
 * Ионизация под действием света. Или фотоэффект.
 * Если элемент наберет достаточно энергии (energyIonization), то электрон может вылететь с орбиты
 */
class StarEmission (
    private val entityGenerator: IEntityGenerator,
) : ReactionRule {
    override val id = "PhotoIonization"

    private var entity : Entity<*>? = null

    override suspend fun matches(reagents: List<Entity<*>>): Boolean {
        entity = null

        if (reagents.isEmpty()) return false

        val first = reagents.first()
        val firstElement = first.state().value.element
        if (firstElement != Element.Star) return false
        if (!first.state().value.alive) return false

        if (!chance(0.002f)) return false // в этом случае он с определенной вероятностью избавится от этой энергии

        entity = first
        return true
    }

    override suspend fun weight() = 0f

    override suspend fun produce(): ReactionOutcome {

        return ReactionOutcome(
            //updateState = listOf { entity!!.addEnergy(-1 * energyToExpose) },
            spawn = listOf {
                entityGenerator.createEntity(
                    Element.Proton,
                    entity!!.state().value.position,
                    randomDirection(),
                    2f,
                    energy = 0f,
                    entity!!.state().value.subEnvironment
                )
            },
            //description = "Люминесценция: ${entityElement.label} (${entityEnergy}eV) -> ${Element.Photon.label} (${energyToExpose}eV)",
        )
    }
}