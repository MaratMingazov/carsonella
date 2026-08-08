package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.SubAtom
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome

/**
 * Фотодиссоциация: фотон достаточной энергии рвёт молекулу по слабейшей связи.
 *
 * Зеркало образования связи (CovalentBondFormation/MoleculeGrowth ИЗЛУЧАЮТ фотон энергии связи) — здесь
 * фотон ПОГЛОЩАЕТСЯ на разрыв: рвём слабейшую связь ([Molecule.weakestBond]), порог = её энергия
 * ([Molecule.dissociationEnergy], кэш на графе). Продукты — ИЗ ТОПОЛОГИИ ([Molecule.split]),
 * а не из хардкода: осколок из одного узла → атом, из ≥2 узлов → молекула. Горячий осколок-молекула может распасться дальше на следующих тиках — рекурсивно
 * до атомов.
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

    private data class Match(val molecule: Molecule, val photon: SubAtom) : MatchedData

    override fun matchesMolecule(molecule: Molecule, neighbors: List<Entity>): MatchedData? {
        if (neighbors.isEmpty()) return null   // рвать некому: фотон приходит соседом

        val threshold = molecule.dissociationEnergy ?: return null // проверяем есть ли у молекулы связь, которую можно порвать?

        val moleculePosition = molecule.state().value.kinematics.position
        val moleculeRadius = molecule.radius
        val activationDistanceSquare = moleculeRadius * moleculeRadius

        val nearestPhoton = neighbors
            .asSequence()
            .filterIsInstance<SubAtom>().filter { it.element == Element.PHOTON }
            .filter { it.energy > 0f && it.state().value.alive }
            .filter { it.getEnvironment() === molecule.getEnvironment() }   // оба в одной среде
            .map { it to moleculePosition.distanceSquareTo(it.state().value.kinematics.position) }
            .filter { it.second <= activationDistanceSquare }
            .minByOrNull { it.second }
            ?.first
            ?: return null

        val available = molecule.energy + nearestPhoton.energy
        if (available < threshold) return null   // фотона не хватает даже на слабейшую связь → пролетает мимо

        return Match(molecule, nearestPhoton)
    }

    // Распад ЭНДОТЕРМИЧЕН — вес отрицательный (контракт weight = энергия реакции со знаком): разрыв связи
    // «стоит» dissociationEnergy. Так распад проигрывает любой ассоциации (рост/усиление, «+») и побеждает
    // только когда строить нечего (напр. насыщенная O=O + фотон — единственный совпавший вариант).
    override fun weight(match: MatchedData): Float {
        val (mol, _) = match as Match
        val threshold = mol.dissociationEnergy ?: return 0f
        return -threshold
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (mol, ph) = match as Match
        val bond = mol.weakestBond!!          // matches гарантирует что не null
        val threshold = bond.energy!!         // связь из каталога — иначе weakestBond её не выбрал бы

        // Избыток (доступная − порог) не теряем (§6/§8, сохранение энергии) — он уходит продуктам.
        // Кольцо это или распад и куда именно кладётся энергия — забота breakBond.
        val available = mol.energy + ph.energy
        val outcome = breakBond(mol, bond, entityGenerator, energyToShare = available - threshold)

        return outcome.copy(consumed = outcome.consumed + ph)   // фотон ПОГЛОЩЁН в любом из случаев
    }
}