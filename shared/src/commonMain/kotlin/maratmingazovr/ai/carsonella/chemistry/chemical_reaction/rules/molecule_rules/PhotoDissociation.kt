package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.chemistry.registry.AtomElement
import maratmingazovr.ai.carsonella.chemistry.SubAtom
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.MoleculeAtom
import maratmingazovr.ai.carsonella.chemistry.MoleculeBond
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome

/**
 * Фотодиссоциация: фотон достаточной энергии рвёт молекулу по связи ТОГО АТОМА, в который попал.
 *
 * Рамки этого шага:
 *  - Только РАСПАД. Фотон ниже порога пролетает мимо (возбуждение/поглощение и молекулярная ионизация —
 *    отдельные будущие правила; вероятностное ветвление распад/ионизация появится вместе с ними).
 *  - Электроны осколков — ГОМОЛИТИЧЕСКИ: каждый осколок нейтрален (electrons = его протоны). Для нейтральной
 *    молекулы сумма сохраняется; ионы-осколки (гетеролитика) — позже.
 *  - Избыток энергии (доступная − порог) НЕ теряется: раскладываем в energy осколков (внутренняя энергия;
 *    так осколок «горячее» и легче распадётся дальше). Порог = энергия связи «тратится» на разрыв — это
 *    зеркало того, что образование связи её высвобождало фотоном (сохранение энергии по циклу).
 */
class PhotoDissociation(private val entityGenerator: IEntityGenerator) : MoleculeReactionRule() {
    override val id = "PhotoDissociation"

    private data class Match(
        val molecule: Molecule,
        val photon: SubAtom,
        val bond: MoleculeBond,   // Какую связь рвём — выбрано здесь; produce и weight не пересчитывают.
        val bondEnergy: Float,    // Порог разрыва (эВ): энергия ЭТОЙ связи, а не слабейшей в молекуле.
    ) : MatchedData

    override fun matchesMolecule(molecule: Molecule, neighbors: List<Entity>): MatchedData? {
        if (neighbors.isEmpty()) return null   // рвать некому: фотон приходит соседом
        if (molecule.dissociationEnergy == null) return null   // ни одной связи из каталога — рвать нечего

        val photons = neighbors
            .filterIsInstance<SubAtom>()
            .filter { it.element == AtomElement.PHOTON && it.energy > 0f && it.alive }
            .filter { it.getEnvironment() === molecule.getEnvironment() }   // оба в одной среде
        if (photons.isEmpty()) return null

        return molecule.atoms
            .flatMap { atom -> candidates(molecule, atom, photons) }
            .minByOrNull { (_, distanceSquare) -> distanceSquare }
            ?.first
    }

    // Фотоны, попавшие в этот атом и осилившие его слабейшую связь, с квадратом расстояния до него.
    private fun candidates(molecule: Molecule, atom: MoleculeAtom, photons: List<SubAtom>): List<Pair<Match, Float>> {
        val (bond, bondEnergy) = molecule.weakestBondAt(atom) ?: return emptyList()
        val activationDistanceSquare = atom.radius * atom.radius
        return photons.mapNotNull { photon ->
            val distanceSquare = atom.kinematics.position.distanceSquareTo(photon.kinematics.position)
            if (distanceSquare > activationDistanceSquare) return@mapNotNull null      // фотон пролетает мимо атома
            if (molecule.energy + photon.energy < bondEnergy) return@mapNotNull null   // не хватает даже на эту связь
            Match(molecule, photon, bond, bondEnergy) to distanceSquare
        }
    }

    override fun weight(match: MatchedData): Float = -(match as Match).bondEnergy

    override fun produce(match: MatchedData): ReactionOutcome {
        val (mol, ph, bond, threshold) = match as Match

        // Избыток (доступная − порог) не теряем (§6/§8, сохранение энергии) — он уходит продуктам.
        // Кольцо это или распад и куда именно кладётся энергия — забота breakBond.
        val available = mol.energy + ph.energy
        val outcome = breakBond(mol, bond, entityGenerator, energyToShare = available - threshold)

        return outcome.copy(consumed = outcome.consumed + ph)   // фотон ПОГЛОЩЁН в любом из случаев
    }
}