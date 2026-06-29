package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule

/**
 * Фотодиссоциация или Фотоэффект
 * Реакция фотодиссоциации, или фотолиза, — это химическое разложение молекул под действием фотонов,
 * где энергия фотона превышает энергию активации молекулы, вызывая её распад на атомы, радикалы или ионы.
 */
class PhotoDissociation(private val entityGenerator: IEntityGenerator, ) : ReactionRule {
    override val id = "PhotoDissociation"

    private var entity : Entity<*>? = null
    private var photon : Entity<*>? = null


    override fun matches(reagents: List<Entity<*>>) : Boolean {
        // ДОРМАНТ (§6 docs/molecule-graph.md). Правило временно отключено: старый matches читал
        // enum-поля (details.energyBondDissociation / dissociationElements) и шов .element реагентов —
        // и падал на граф-молекулах. Enum-молекулы больше не спавнятся (живых входов нет), а граф-
        // диссоциация — отдельный рефактор: какая связь рвётся, надо вычислять ИЗ ГРАФА, а не из
        // хардкодженного dissociationElements. До него правило не матчится. produce() ниже сохранён
        // как референс старого поведения (порог энергии, спавн осколков) для будущей граф-версии.
        return false
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {

        /**
         *  Энергетический порог молекулы.
         *  Если в молекулу прилетел фотон, то молекула либо заберет эту энергию, либо сама распадется
         */
        val energyDissociation = entity!!.state().value.element.details.energyBondDissociation!!
        val entityEnergy = entity!!.state().value.energy
        val photonEnergy = photon!!.state().value.energy

        if (entityEnergy + photonEnergy < energyDissociation) {
            // мы поглащаем энергию, так как порог еще не пройден
            return ReactionOutcome(
                consumed = listOf(photon!!),
                updateState = listOf { entity!!.addEnergy(photonEnergy) },
            )
        } else {
            // пройден энергетический порог. Происходит диссоциация
            val entityPosition = entity!!.state().value.position
            val entityElement = entity!!.state().value.element
            val entityDirection = entity!!.state().value.direction
            val entityVelocity = entity!!.state().value.velocity
            val dissociationElements = entityElement.details.dissociationElements
            val dissociationElement1 = dissociationElements[0]
            val dissociationElement2 = dissociationElements[1]

            val env = entity!!.getEnvironment()
            return ReactionOutcome(
                consumed = listOf(photon!!, entity!!),
                spawn = listOf {
                    entityGenerator.createEntity(
                        dissociationElement1,
                        entityPosition.plus(Position(-1f * entityElement.details.radius, 0f)),
                        entityDirection,
                        entityVelocity,
                        energy = 0f,
                        environment = env,
                        electrons = dissociationElement1.details.p, // нейтральный осколок
                    )
                    entityGenerator.createEntity(
                        dissociationElement2,
                        entityPosition.plus(Position(1f * entityElement.details.radius, 0f)),
                        entityDirection,
                        entityVelocity,
                        energy = 0f,
                        environment = env,
                        electrons = dissociationElement2.details.p, // нейтральный осколок
                    )
                },
            )
        }

    }


}