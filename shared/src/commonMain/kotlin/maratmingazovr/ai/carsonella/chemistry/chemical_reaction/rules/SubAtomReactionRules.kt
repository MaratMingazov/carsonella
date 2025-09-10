package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.Electron
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IAtomGenerator

class ElectronPlusProtonToH(
    private val atomGenerator: IAtomGenerator,
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
        val activationDistanceSquare = 100f

        fun dist(a: Entity<*>, b: Entity<*>): Float {
            val pa = a.state().value.position
            val pb = b.state().value.position
            return pa.distanceSquareTo(pb) // твоя функция расстояния в «пикселях»
        }
        Electron

        when (first.state().value.element) {
            Electron -> {
                if (!first.state().value.alive) return false
                val nearestProton = others
                    .asSequence()
                    .filter { it.state().value.element == Proton }
                    .filter { it.state().value.alive }
                    .minByOrNull { dist(first, it) }
                    ?: return false

                if (dist(first, nearestProton) <= activationDistanceSquare) {
                    electron = first
                    proton = nearestProton
                    return true
                }
            }

            Proton -> {
                if (!first.state().value.alive) return false
                val nearestElectron = others
                    .asSequence()
                    .filter { it.state().value.element == Electron }
                    .filter { it.state().value.alive }
                    .minByOrNull { dist(first, it) }
                    ?: return false

                if (dist(first, nearestElectron) <= activationDistanceSquare) {
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
            spawn = listOf { atomGenerator.createAtom(Element.H, proton!!.state().value.position, direction, velocity, energy = 0f) },
        )
    }
}