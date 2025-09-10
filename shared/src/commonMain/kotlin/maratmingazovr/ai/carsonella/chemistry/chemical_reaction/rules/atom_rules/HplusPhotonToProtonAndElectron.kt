package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.H
import maratmingazovr.ai.carsonella.chemistry.Element.Photon
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Element.Electron
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.ISubAtomGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule
import maratmingazovr.ai.carsonella.randomDirection

class HplusPhotonToProtonAndElectron(
    private val subAtomGenerator: ISubAtomGenerator,
) : ReactionRule {
    override val id = "H+photon->ProtonAndElectron"

    private var hydrogen : Entity<*>? = null
    private var photon : Entity<*>? = null


    override suspend fun matches(reagents: List<Entity<*>>) : Boolean {
        hydrogen = null
        photon = null

        if (reagents.size < 2) return false

        val first = reagents.first()
        if (first.state().value.element != H) return false
        if (!first.state().value.alive) return false
        val others = reagents.drop(1)
        val activationDistanceSquare = H.radius * H.radius

        val (nearestPhoton, distance) = others
            .asSequence()
            .filter { it.state().value.element == Photon }
            .filter { it.state().value.alive }
            .map { it to first.state().value.position.distanceSquareTo(it.state().value.position) }
            .minByOrNull { it.second }
            ?: return false

        if (distance <= activationDistanceSquare) {
            hydrogen = first
            photon = nearestPhoton
            return true
        }
        return false
    }

    override suspend fun weight() = 0f

    override suspend fun produce(): ReactionOutcome {

        //val H_ev = 13.6f // энергия связи атома водорода
        val H_ev = 5f // энергия связи атома водорода
        if (hydrogen!!.state().value.energy + photon!!.state().value.energy < H_ev) {
            // энергии фотона не хватает, чтобы выбить электрон, поэтому атом водорода поглотить эту энергию
            return ReactionOutcome(
                consumed = listOf(photon!!),
                updateState = listOf { hydrogen!!.addEnergy(photon!!.state().value.energy) },
            )
        } else {
            // фотон выбивает электрон у атома водорода.
            val freeEnergy = hydrogen!!.state().value.energy + photon!!.state().value.energy - H_ev
            val electronDiration = randomDirection()
            val protonDirection = Vec2D(-1*electronDiration.x, -1*electronDiration.y)
            return ReactionOutcome(
                consumed = listOf(photon!!, hydrogen!!),
                spawn = listOf {
                    subAtomGenerator.createSubAtom(Proton, hydrogen!!.state().value.position.plus(Position(H.radius,0f)), protonDirection, freeEnergy, energy = 0f)
                    subAtomGenerator.createSubAtom(Electron, hydrogen!!.state().value.position, electronDiration, 40f, energy = 0f)
                               },
            )
        }

    }


}