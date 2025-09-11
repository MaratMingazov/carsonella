package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element.*
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.ISubAtomGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule
import maratmingazovr.ai.carsonella.randomDirection


/**
 * Фотоэффект.
 * В игре реализовал фотоэлектрический эффект - явление выбивания электрона из атома водорода под действием света.
 * У атома водорода есть 1 протон и 1 электрон. Они связаны между собой энергией 13.6 эВ.
 * Если мы начнем стрелять фотонами в наш атома водорода, то мы можем выбить этот электрон.
 * Для этого нам нужно превысить энергию связи в 13.6 эВ.
 * Фотоны (в зависимости от длины волны) несут в себе разную энергию.
 * Например, красный фотон (1.8эВ), фиолетовый фотон (3.1эВ), или рентгеновский фотон (1240эВ)
 *
 * Что происходит, когда мы начинаем фотоном атаковать наш атом водорода:
 * 1 - Если у фотона энергии мало, то он просто пролетит мимо, а атом водорода получит крошечный импульс
 * 2 - Если у фотона энергии больше, то атом водорода может перейти в возбужденное состояние. Электрон поднимается на уровень выше (энергия 10.2эВ). Но это не стабильное состояние атома водорода. Поэтому через какое то время атом водорода сам испускает фотон, чтобы выпустить эту лишнюю энергию, электрон снова перейдет на свою стабильное состояние.
 * 3 - Если мы превысим энергию связи, то атом водорода разлетится на части. Электрон сможет преодолеть притяжение протона и улетит от него
 */
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

        val H_ev = 13.6f // энергия связи атома водорода
        if (hydrogen!!.state().value.energy + photon!!.state().value.energy < H_ev) {
            // энергии фотона не хватает, чтобы выбить электрон, поэтому атом водорода поглотить эту энергию
            return ReactionOutcome(
                consumed = listOf(photon!!),
                updateState = listOf { hydrogen!!.addEnergy(photon!!.state().value.energy) },
            )
        } else {
            // фотон выбивает электрон у атома водорода.
            val freeEnergy = hydrogen!!.state().value.energy + photon!!.state().value.energy - H_ev
            val electronDirection = randomDirection()
            val protonDirection = Vec2D(-1*electronDirection.x, -1*electronDirection.y)
            return ReactionOutcome(
                consumed = listOf(photon!!, hydrogen!!),
                spawn = listOf {
                    subAtomGenerator.createSubAtom(Proton, hydrogen!!.state().value.position.plus(Position(H.radius,0f)), protonDirection, freeEnergy, energy = 0f)
                    subAtomGenerator.createSubAtom(Electron, hydrogen!!.state().value.position, electronDirection, 40f, energy = 0f)
                               },
            )
        }

    }


}