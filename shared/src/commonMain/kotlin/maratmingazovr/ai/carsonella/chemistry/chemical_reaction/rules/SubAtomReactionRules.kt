package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.Electron
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator

class ElectronPlusProtonToH(
    private val entityGenerator: IEntityGenerator,
) : ReactionRule {
    override val id = "e+p->H"

    private var electron : Entity<*>? = null
    private var proton : Entity<*>? = null


    /**
     * Этот метод проверяет возможна ли реакция электрона и протона для образования атома водорода
     * Первый реагент обязательно должен быть либо электрон, либо протон
     * Если первый реагент электрон, то в списке нужно поискать протон который находится близко к протону
     * Если первый реагент протон, тогда нужно поискать электрон
     * Если мы нашли такие электрон и протон, то реакция возможна, иначе нет
     */
    override suspend fun matches(reagents: List<Entity<*>>) : Boolean {
        electron = null
        proton = null
        if (reagents.size < 2) return false

        val first = reagents.first()
        val others = reagents.drop(1)
        val activationDistanceSquare = 2f * Proton.radius * Proton.radius

        when (first.state().value.element) {
            Electron -> {
                if (!first.state().value.alive) return false
                val (nearestProton, distance) = others
                    .asSequence()
                    .filter { it.state().value.element == Proton }
                    .filter { it.state().value.alive }
                    .map { it to first.state().value.position.distanceSquareTo(it.state().value.position) }
                    .minByOrNull { it.second }
                    ?: return false


                if (distance <= activationDistanceSquare) {
                    electron = first
                    proton = nearestProton
                    return true
                }
            }

            Proton -> {
                if (!first.state().value.alive) return false
                val (nearestElectron, distance) = others
                    .asSequence()
                    .filter { it.state().value.element == Electron }
                    .filter { it.state().value.alive }
                    .map { it to first.state().value.position.distanceSquareTo(it.state().value.position) }
                    .minByOrNull { it.second }
                    ?: return false

                if (distance <= activationDistanceSquare) {
                    electron = nearestElectron
                    proton = first
                    return true
                }
            }

            else -> return false
        }
        return false

    }

    override suspend fun weight() = 1f

    override suspend fun produce(): ReactionOutcome {

        val (direction,velocity) = calculateHydrogenDirectionAndVelocity(electron!!, proton!!)
        return ReactionOutcome(
            consumed = listOf(electron!!, proton!!),
            spawn = listOf { entityGenerator.createEntity(Element.H, proton!!.state().value.position, direction, velocity, energy = 0f) },
        )
    }
}