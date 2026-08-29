package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.ReactionSelection
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ForcedReactionRule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.bondPhoton
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StateUpdate
import maratmingazovr.ai.carsonella.chemistry.graph.BondEnergy

// Замыкание кольца: два ненасыщенных атома ОДНОЙ молекулы связываются → цикл (циклопентан, бензол-скелет, дальше листы/каркасы).
class RingClosure(
    private val entityGenerator: IEntityGenerator,
) : ForcedReactionRule {
    override val id = "RingClosure"

    private data class Match(val molecule: Molecule, val localId1: Int, val localId2: Int) : MatchedData

    // Пару атомов выбирает игрок (клик по атомам молекулы) — правило только проверяет, что она годная.
    override fun matches(reagents: List<Entity>, selection: ReactionSelection.Forced): MatchedData? {
        val choice = selection as? ReactionSelection.CloseRing ?: return null   // чужой выбор — не наш
        if (reagents.size != 1) return null   // форс приходит self-запросом (World.requestMoleculeAction)
        val molecule = reagents.first() as? Molecule ?: return null
        if (!molecule.alive) return null
        if (molecule.getEnvironment().getEnvTemperature() == TemperatureMode.Star) return null   // в звезде молекул нет
        if (!molecule.canCloseRing(choice.localId1, choice.localId2)) return null   // свободный слот у обоих концов, кольцо не короче минимума
        if (closureEnergy(molecule, choice.localId1, choice.localId2) == null) return null   // энергия такой связи неизвестна (не CHNO)
        return Match(molecule, choice.localId1, choice.localId2)
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (molecule, localId1, localId2) = match as Match
        val env = molecule.getEnvironment()
        val released = closureEnergy(molecule, localId1, localId2) ?: 0f

        val spawn = mutableListOf<() -> Entity>()
        if (released > 0f) {
            // Энергию высвободила новая связь — на ней фотон и рождается (см. bondPhoton). Отступ тут
            // особенно к месту: замыкание решается по длине пути в графе, а не по близости концов, так что
            // связываемые атомы могут стоять и вплотную.
            val atom1 = molecule.atom(localId1)
            val atom2 = molecule.atom(localId2)
            val photon = bondPhoton(
                atom1.kinematics.position, atom1.radius,
                atom2.kinematics.position, atom2.radius,
                entityGenerator.random,
            )
//            spawn += {
//                entityGenerator.createEntity(Element.PHOTON, photon.position, photon.direction,
//                    MAX_VELOCITY, energy = released, environment = env, electrons = 0)
//            }
        }

        return ReactionOutcome(
            spawn = spawn,
            updateState = listOf(StateUpdate(molecule) { molecule.closeRing(localId1, localId2) }),
        )
    }

    // Сколько эВ высвободит новая связь.
    private fun closureEnergy(molecule: Molecule, localId1: Int, localId2: Int): Float? =
        BondEnergy.of(molecule.atom(localId1).isotope, molecule.atom(localId2).isotope, 1)
}