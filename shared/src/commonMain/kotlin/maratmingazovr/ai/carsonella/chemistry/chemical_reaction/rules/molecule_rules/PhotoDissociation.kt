package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome

/**
 * Фотодиссоциация: фотон достаточной энергии рвёт молекулу по слабейшей связи.
 *
 * Зеркало образования связи (CovalentBondFormation/MoleculeGrowth ИЗЛУЧАЮТ фотон энергии связи) — здесь
 * фотон ПОГЛОЩАЕТСЯ на разрыв: рвём слабейшую связь ([MoleculeGraph.weakestBond]), порог = её энергия
 * ([MoleculeGraph.dissociationEnergy], кэш на графе). Продукты — ИЗ ТОПОЛОГИИ ([MoleculeGraph.split]),
 * а не из хардкода: осколок из одного узла → атом ([Species.Atomic]), из ≥2 узлов → молекула
 * ([Species.Molecular]). Горячий осколок-молекула может распасться дальше на следующих тиках — рекурсивно
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

    private data class Match(val molecule: Entity, val photon: Entity) : MatchedData

    override fun matchesMolecule(reagents: List<Entity>): MatchedData? {
        if (reagents.size < 2) return null

        val first = reagents.first()
        if (!first.state().value.alive) return null
        val graph = (first.state().value.species as Species.Molecular).graph
        val weakestBondAndEnergy = graph.weakestBondAndEnergy ?: return null // проверяем есть ли у молекулы связь, которую можно порвать?
        val threshold = weakestBondAndEnergy.second

        val firstPosition = first.state().value.centerPosition
        val radius = first.state().value.radius
        val activationDistanceSquare = radius * radius

        val nearestPhoton = reagents.drop(1)
            .asSequence()
            .filter { val sp = it.state().value.species; sp is Species.Atomic && sp.element == Element.PHOTON }
            .filter { it.state().value.energy > 0f && it.state().value.alive }
            .filter { it.getEnvironment() === first.getEnvironment() }   // оба в одной среде
            .map { it to firstPosition.distanceSquareTo(it.state().value.centerPosition) }
            .filter { it.second <= activationDistanceSquare }
            .minByOrNull { it.second }
            ?.first
            ?: return null

        val available = first.state().value.energy + nearestPhoton.state().value.energy
        if (available < threshold) return null   // фотона не хватает даже на слабейшую связь → пролетает мимо

        return Match(first, nearestPhoton)
    }

    // Распад ЭНДОТЕРМИЧЕН — вес отрицательный (контракт weight = энергия реакции со знаком): разрыв связи
    // «стоит» dissociationEnergy. Так распад проигрывает любой ассоциации (рост/усиление, «+») и побеждает
    // только когда строить нечего (напр. насыщенная O=O + фотон — единственный совпавший вариант).
    override fun weight(match: MatchedData): Float {
        val (mol, _) = match as Match
        val graph = (mol.state().value.species as Species.Molecular).graph
        val threshold = graph.weakestBondAndEnergy?.second ?: return 0f
        return -threshold
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (mol, ph) = match as Match
        val graph = (mol.state().value.species as Species.Molecular).graph
        val weakestBondAndEnergy = graph.weakestBondAndEnergy!! // matches гарантирует что не null
        val bond = weakestBondAndEnergy.first
        val threshold = weakestBondAndEnergy.second

        val fragments = graph.split(bond.atom1, bond.atom2)

        // Избыток (доступная − порог) делим поровну на осколки — не теряем (§6/§8, сохранение энергии).
        // Куда именно кладём долю (внутренняя энергия молекулы / кинетика атома) — решает spawnFragments.
        val available = mol.state().value.energy + ph.state().value.energy
        val excessPerFragment = (available - threshold).coerceAtLeast(0f) / fragments.size

        val spawn = spawnFragments(fragments, mol, entityGenerator, excessPerFragment)

        return ReactionOutcome(
            consumed = listOf(ph, mol),
            spawn = spawn,
            description = "$id: ${graph.formulaPretty} + γ[${ph.state().value.energy}eV] -> " +
                fragments.joinToString(" + ") { it.formulaPretty },
        )
    }
}