package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.MoleculeRingCandidate
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
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

    private data class Match(val molecule: Molecule, val candidate: MoleculeRingCandidate) : MatchedData

    override fun matches(reagents: List<Entity>, selection: ReactionSelection.Forced): MatchedData? {
        if (selection !is ReactionSelection.CloseRing) return null   // чужой выбор — не наш
        if (reagents.size != 1) return null   // форс приходит self-запросом (World.requestMoleculeAction)
        val molecule = reagents.first() as? Molecule ?: return null
        if (!molecule.alive) return null
        if (molecule.getEnvironment().getEnvTemperature() == TemperatureMode.Star) return null   // в звезде молекул нет
        // Кандидат с максимальным выигрышем (энергия связи − напряжение): 5–6 бьют 7+.
        // null-выигрыш (энергия связи неизвестна) отсеиваем.
        val candidate = molecule.ringClosureCandidates
            .mapNotNull { cand -> closureWeight(molecule, cand)?.let { cand to it } }
            .maxByOrNull { it.second }
            ?: return null
        return Match(molecule, candidate.first)
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (molecule, cand) = match as Match
        val env = molecule.getEnvironment()
        val released = closureWeight(molecule, cand) ?: 0f

        val spawn = mutableListOf<() -> Entity>()
        if (released > 0f) {
            // Энергию высвободила новая связь — на ней фотон и рождается (см. bondPhoton). Отступ тут
            // особенно к месту: замыкание решается по длине пути в графе, а не по близости концов, так что
            // связываемые атомы могут стоять и вплотную.
            val atom1 = molecule.atom(cand.localId1)
            val atom2 = molecule.atom(cand.localId2)
            val photon = bondPhoton(
                atom1.kinematics.position, atom1.radius,
                atom2.kinematics.position, atom2.radius,
                entityGenerator.random,
            )
            spawn += {
                entityGenerator.createEntity(Element.PHOTON, photon.position, photon.direction,
                    MAX_VELOCITY, energy = released, environment = env, electrons = 0)
            }
        }

        return ReactionOutcome(
            spawn = spawn,
            updateState = listOf(StateUpdate(molecule) { molecule.closeRing(cand.localId1, cand.localId2) }),
        )
    }

    // weight замыкания: энергия образуемой связи (BondEnergy, order=1) − напряжение кольца.
    // null, если энергия связи неизвестна (не CHNO) — тогда кандидат пропускается.
    // Связи ещё нет, поэтому её энергии нет и в кеше графа — идём в каталог (в отличие от MoleculeBond.energy).
    private fun closureWeight(molecule: Molecule, cand: MoleculeRingCandidate): Float? {
        val bondE = BondEnergy.of(molecule.atom(cand.localId1).isotope, molecule.atom(cand.localId2).isotope, 1) ?: return null
        return bondE - ringStrain(cand.ringSize)
    }
}

// Байеровское напряжение кольца по числу атомов (эВ; порядок реальных значений: ккал/моль → эВ). 3–4 сильно
// напряжены, 5–6 почти/без напряжения, 7 чуть, 8+ мягко растёт (трансаннулярное/энтропия). 3–4 сюда не
// доходят — их отсекает пол RING_MIN_SIZE в ringClosureCandidates; оставлены для полноты/устойчивости.
private fun ringStrain(size: Int): Float = when (size) {
    3 -> 1.17f
    4 -> 1.13f
    5 -> 0.29f
    6 -> 0.0f
    7 -> 0.29f
    else -> 0.40f + 0.05f * (size - 8).coerceAtLeast(0)
}