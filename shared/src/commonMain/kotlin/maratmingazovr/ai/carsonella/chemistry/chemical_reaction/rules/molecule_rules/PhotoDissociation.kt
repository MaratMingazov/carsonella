package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.radius

/**
 * Фотодиссоциация (§6 docs/molecule-graph.md): фотон достаточной энергии рвёт молекулу по слабейшей связи.
 *
 * Зеркало образования связи (CovalentBondFormation/MoleculeGrowth ИЗЛУЧАЮТ фотон энергии связи) — здесь
 * фотон ПОГЛОЩАЕТСЯ на разрыв: рвём слабейшую связь ([MoleculeGraph.weakestBond]), порог = её энергия
 * ([MoleculeGraph.dissociationEnergy], кэш на графе). Продукты — ИЗ ТОПОЛОГИИ ([MoleculeGraph.split]),
 * а не из хардкода: осколок из одного узла → атом ([Species.Elemental]), из ≥2 узлов → молекула
 * ([Species.Molecular]). Горячий осколок-молекула может распасться дальше на следующих тиках — рекурсивно
 * до атомов.
 *
 * Рамки этого шага (см. §6):
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

    private var molecule: Entity<*>? = null
    private var photon: Entity<*>? = null

    override fun matchesMolecule(reagents: List<Entity<*>>): Boolean {
        molecule = null
        photon = null
        if (reagents.size < 2) return false

        val first = reagents.first()
        if (!first.state().value.alive) return false
        val graph = (first.state().value.species as Species.Molecular).graph
        val threshold = graph.dissociationEnergy ?: return false   // рвать нечего (нет связей / тип не в каталоге)

        val firstPosition = first.state().value.position
        val radius = first.state().value.species.radius()
        val activationDistanceSquare = radius * radius

        val nearestPhoton = reagents.drop(1)
            .asSequence()
            .filter { val sp = it.state().value.species; sp is Species.Elemental && sp.element == Element.PHOTON }
            .filter { it.state().value.energy > 0f && it.state().value.alive }
            .filter { it.getEnvironment() === first.getEnvironment() }   // оба в одной среде
            .map { it to firstPosition.distanceSquareTo(it.state().value.position) }
            .filter { it.second <= activationDistanceSquare }
            .minByOrNull { it.second }
            ?.first
            ?: return false

        val available = first.state().value.energy + nearestPhoton.state().value.energy
        if (available < threshold) return false   // фотона не хватает даже на слабейшую связь → пролетает мимо

        molecule = first
        photon = nearestPhoton
        return true
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {
        val mol = molecule!!
        val ph = photon!!
        val graph = (mol.state().value.species as Species.Molecular).graph
        val bond = graph.weakestBond!!             // matches гарантировал dissociationEnergy != null → weakestBond != null
        val threshold = graph.dissociationEnergy!!

        val fragments = graph.split(bond.atom1, bond.atom2)

        // Избыток (доступная − порог) делим поровну в energy осколков — не теряем (§6/§8, сохранение энергии).
        val available = mol.state().value.energy + ph.state().value.energy
        val excessPerFragment = (available - threshold).coerceAtLeast(0f) / fragments.size

        val molPosition = mol.state().value.position
        val molDirection = mol.state().value.direction
        val molVelocity = mol.state().value.velocity
        val env = mol.getEnvironment()
        val radius = mol.state().value.species.radius()

        val spawn: List<() -> Entity<*>> = fragments.mapIndexed { i, frag ->
            // разводим осколки по оси X, чтобы не появлялись в одной точке
            val pos = molPosition.plus(Position((i - (fragments.size - 1) / 2f) * radius, 0f))
            val electrons = frag.protons               // нейтральный осколок (гомолитика)
            val species: Species =
                if (frag.nodes.size == 1) Species.Elemental(frag.nodes.single().isotope) else Species.Molecular(frag)
            return@mapIndexed { entityGenerator.createEntity(species, pos, molDirection, molVelocity, excessPerFragment, env, electrons) }
        }

        return ReactionOutcome(
            consumed = listOf(ph, mol),
            spawn = spawn,
            description = "$id: ${graph.formulaPretty()} + γ[${ph.state().value.energy}eV] -> " +
                fragments.joinToString(" + ") { it.formulaPretty() },
        )
    }
}