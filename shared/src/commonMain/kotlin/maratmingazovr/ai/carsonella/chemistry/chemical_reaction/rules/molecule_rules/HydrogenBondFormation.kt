package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.HydrogenBond
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.MoleculeAtom
import maratmingazovr.ai.carsonella.chemistry.hydrogenBondRestLength
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StateUpdate

/**
 * Образование водородной связи: донорный водород одной молекулы сблизился с акцептором другой.
 *
 * Молекул не потребляет и не создаёт — только рождает [HydrogenBond]. Вода остаётся водой: связь
 * межмолекулярная, идентичность вещества не меняется.
 */
class HydrogenBondFormation(
    private val entityGenerator: IEntityGenerator,
) : MoleculeReactionRule() {
    override val id = "HydrogenBond"

    private data class Match(
        val molecule1: Molecule, val atom1: MoleculeAtom,   // донор: H на N/O/F
        val molecule2: Molecule, val atom2: MoleculeAtom,   // акцептор: N/O/F
    ) : MatchedData

    override fun matchesMolecule(molecule: Molecule, neighbors: List<Entity>): MatchedData? {
        val partners = neighbors
            .filterIsInstance<Molecule>()
            .filter { it.alive && it !== molecule && it.getEnvironment() === molecule.getEnvironment() }
        if (partners.isEmpty()) return null

        // Живой список связей, а НЕ из neighbors: тот снят в фазе step, а resolve идёт по инициаторам
        // последовательно — связь, только что созданную соседней молекулой, видно лишь свежим запросом.
        val existing = molecule.getNeighbors().filterIsInstance<HydrogenBond>().filter { it.alive }

        return molecule.atoms
            .flatMap { own -> candidates(molecule, own, partners, existing) }
            .minByOrNull { (_, distanceSquare) -> distanceSquare }
            ?.first
    }

    // Пары «этот атом + атом соседа», годные под связь, с квадратом расстояния между ними.
    private fun candidates(
        molecule: Molecule,
        own: MoleculeAtom,
        partners: List<Molecule>,
        existing: List<HydrogenBond>,
    ): List<Pair<Match, Float>> {
        val ownIsDonor = isDonor(molecule, own)
        val ownIsAcceptor = isAcceptor(own)
        if (!ownIsDonor && !ownIsAcceptor) return emptyList()

        return partners.flatMap { partner ->
            partner.atoms.mapNotNull { other ->
                // Кто из пары донор, а кто акцептор — решаем здесь: связь направленная, H···N/O/F.
                val match = when {
                    ownIsDonor && isAcceptor(other) -> Match(molecule, own, partner, other)
                    ownIsAcceptor && isDonor(partner, other) -> Match(partner, other, molecule, own)
                    else -> return@mapNotNull null
                }
                val distanceSquare = own.kinematics.position.distanceSquareTo(other.kinematics.position)
                val capture = hydrogenBondRestLength(own, other)
                if (distanceSquare > capture * capture) return@mapNotNull null   // ещё не сблизились
                val duplicate = existing.any {
                    it.connectsSamePair(match.molecule1.id, match.atom1.localId, match.molecule2.id, match.atom2.localId)
                }
                if (duplicate) return@mapNotNull null   // связь тут уже есть
                match to distanceSquare
            }
        }
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (molecule1, atom1, molecule2, atom2) = match as Match
        return ReactionOutcome(
            spawn = listOf {
                entityGenerator.createHydrogenBond(molecule1, atom1, molecule2, atom2, molecule1.getEnvironment())
            },
            // Мутации нет — молекулы попадают в исход только чтобы лог читался как «H₂O + H₂O -> … + H···O».
            updateState = listOf(StateUpdate(molecule1) {}, StateUpdate(molecule2) {}),
        )
    }

    /**
     * Донор — водород, чья ковалентная связь сильно поляризована, то есть партнёр электроотрицательный.
     * Именно поэтому H на N/O/F донор, а H на углероде (EN 2.55) — нет: отсюда метан газ, а вода жидкость.
     */
    private fun isDonor(molecule: Molecule, atom: MoleculeAtom): Boolean {
        if (atom.isotope.details.p != 1) return false   // H и D — одновалентные, всегда концевые
        return molecule.bonds
            .filter { it.localId1 == atom.localId || it.localId2 == atom.localId }
            .any { bond ->
                val partnerId = if (bond.localId1 == atom.localId) bond.localId2 else bond.localId1
                isElectronegative(molecule.atom(partnerId))
            }
    }

    // Акцептор — сам электроотрицательный атом (N/O/F). Неподелённые пары (они превратят лёд в решётку,
    // а не в комок) пока не считаем: у кислорода воды акцепторный слот есть всегда.
    private fun isAcceptor(atom: MoleculeAtom): Boolean = isElectronegative(atom)

    private fun isElectronegative(atom: MoleculeAtom): Boolean =
        (atom.isotope.details.electronegativity ?: 0f) >= MIN_ELECTRONEGATIVITY
}

// Порог «электроотрицательный» по Полингу: 3.0 попадает точно между углеродом (2.55) и азотом (3.04),
// то есть отделяет N/O/F от C — ровно ту границу, на которой водородная связь и появляется.
private const val MIN_ELECTRONEGATIVITY = 3.0f