package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.ElementType
import maratmingazovr.ai.carsonella.chemistry.Element.PHOTON
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule

class AtomPlusAtomToMolecule(
    private val entityGenerator: IEntityGenerator,      // вот сюда нужно будет передать лямбду, с помощью которой можно создать молекулу водорода H2
    private val element1: Element,
    private val element2: Element,
    private val resultElement: Element,
    private val resultPhotonEnergy: Float = 0f, // в результате реакции может выделяться энергия в виде фотонов в эВ.
    private val temperatureMode: TemperatureMode = TemperatureMode.Space,
    private val resultElement2: Element? = null,
    private val resultElement3: Element? = null,
) : ReactionRule {
    override val id = "AtomPlusAtomToMolecule"

    private var atom1 : Entity<*>? = null
    private var atom2 : Entity<*>? = null

    // Реагент подходит: для атома-цели — тот же изотоп и нейтральный (electrons==Z); для молекулы — точное совпадение.
    // Изотоп+нейтраль вместо точной константы: после переключателя цикла нейтральный атом может иметь не «нейтральную»
    // константу (рекомбинировал из голого ядра). Сейчас (electrons==details.e) множество то же → no-op.
    private fun matchesReagent(e: Entity<*>, target: Element): Boolean {
        val el = e.state().value.element
        return if (target.details.type == ElementType.Atom)
            el.details.p == target.details.p && el.details.n == target.details.n && e.state().value.electrons == target.details.p
        else el == target
    }

    override fun matches(reagents: List<Entity<*>>) : Boolean {
        atom1 = null
        atom2 = null
        if (reagents.size < 2) return false
        val firstAtom = reagents.first()
        val firstAtomPosition = reagents.first().state().value.position
        if (!matchesReagent(firstAtom, element1)) return false

        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filter { matchesReagent(it, element2) }
            .filter { it.state().value.alive }
            .map { it to  it.state().value.position.distanceSquareTo(firstAtomPosition)}
            .minByOrNull { it.second }
            ?: return false

        if (firstAtom.getEnvironment().getEnvTemperature() != temperatureMode) return false
        if (secondAtom.getEnvironment().getEnvTemperature() != temperatureMode) return false

        return if (distanceSquare < element1.details.radius * element2.details.radius * 2f) {
            atom1 = firstAtom
            atom2 = secondAtom
            true
        } else {
            false
        }
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {

        val (direction,velocity) = calculateNewEntityDirectionAndVelocity(atom1!!, atom2!!)
        val spawnList = mutableListOf<() -> Entity<*>>()
        val resultPosition = atom1!!.state().value.position
        val atom1Element = atom1!!.state().value.element
        val atom2Element = atom2!!.state().value.element

        spawnList += {
            entityGenerator.createEntity(
                resultElement,
                resultPosition,
                direction,
                velocity,
                energy = atom1!!.state().value.energy + atom2!!.state().value.energy,
                atom1!!.getEnvironment(),
            )
        }

        if (resultElement2 != null) {
            spawnList += {
                entityGenerator.createEntity(
                    resultElement2,
                    Position(resultPosition.x + 1.5f * direction.x * resultElement.details.radius,resultPosition.y),
                    direction,
                    velocity,
                    energy = 0f,
                    environment = atom1!!.getEnvironment(),
                )
            }

        }

        if (resultElement3 != null) {
            spawnList += {
                entityGenerator.createEntity(
                    resultElement3,
                    Position(resultPosition.x - 1.5f * direction.x * resultElement.details.radius,resultPosition.y),
                    direction,
                    velocity,
                    energy = 0f,
                    environment = atom1!!.getEnvironment(),
                )
            }

        }

        if (resultPhotonEnergy > 0) {
            spawnList += {
                entityGenerator.createEntity(
                    PHOTON,
                    Position(resultPosition.x + 1.5f * direction.x * resultElement.details.radius,resultPosition.y + 1.5f * direction.y * resultElement.details.radius),
                    direction,
                    10f,
                    energy = resultPhotonEnergy,
                    environment = atom1!!.getEnvironment(),
                )
            }

        }

        return ReactionOutcome(
            consumed = listOf(atom1!!, atom2!!),
            spawn = spawnList,
            description = "$id: ${atom1Element.details.symbol} + ${atom2Element.details.symbol} -> ${resultElement.details.symbol} + ${PHOTON.details.symbol} [$resultPhotonEnergy ev]"
        )
    }
}
