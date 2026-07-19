package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.radius

/**
 * Термическая диссоциация в звезде (§6 docs/molecule-graph.md, «распад по среде»): молекула в горячей
 * звёздной среде рвётся по слабейшей связи КАЖДЫЙ тик — рекурсивно до атомов. Дальше атомы термически
 * ионизуются ([StarThermalIonization]) → плазма.
 *
 * Зеркало [StarThermalIonization] для атомов (атом в звезде теряет по электрону за тик до голого ядра):
 * триггер — САМА СРЕДА (Star), а не фотон; тепловой энергии в звезде с избытком, поэтому порога/фотона
 * НЕТ (в отличие от [PhotoDissociation]). Внутримолекулярная реакция «сам с собой» (`reagents.size == 1`),
 * как распады/усиление: молекула зовёт себя из `Molecule.step`, когда она в звезде.
 *
 * Рекурсия — сама собой: осколок-молекула на следующем тике снова в звезде → снова рвётся, пока не
 * останутся атомы. Порядок разрыва (слабейшая связь) на финал не влияет — всё равно всё распадётся —
 * но переиспользует [MoleculeGraph.weakestBond]/[MoleculeGraph.split] (общая графовая хирургия) и физически
 * осмыслен (у слабейшей связи самый низкий барьер). Энергия осколков — доля энергии молекулы: разрыв
 * оплачивает тепловая ванна звезды (её не тратим), собственную энергию молекулы не теряем.
 */
class StarDissociation(private val entityGenerator: IEntityGenerator) : MoleculeReactionRule() {
    override val id = "StarDissociation"

    private var molecule: Entity<*>? = null

    override fun matchesMolecule(reagents: List<Entity<*>>): Boolean {
        molecule = null
        if (reagents.size != 1) return false   // «сам с собой», как распады/усиление/термоионизация атома
        val first = reagents.first()
        if (!first.state().value.alive) return false
        if (first.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false
        val graph = (first.state().value.species as Species.Molecular).graph
        if (graph.weakestBondAndEnergy == null) return false   // рвать нечего (нет связей / тип не в каталоге)
        molecule = first
        return true
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {
        val mol = molecule!!
        val graph = (mol.state().value.species as Species.Molecular).graph
        val bond = graph.weakestBondAndEnergy!!.first             // matches гарантировал наличие связи
        val fragments = graph.split(bond.atom1, bond.atom2)

        val molPosition = mol.state().value.position
        val molDirection = mol.state().value.direction
        val molVelocity = mol.state().value.velocity
        val energyPerFragment = mol.state().value.energy / fragments.size   // делим энергию молекулы (разрыв платит звезда)
        val env = mol.getEnvironment()
        val radius = mol.state().value.species.radius()

        val spawn: List<() -> Entity<*>> = fragments.mapIndexed { i, frag ->
            val pos = molPosition.plus(Position((i - (fragments.size - 1) / 2f) * radius, 0f))
            val electrons = frag.protons               // нейтральный осколок (гомолитика)
            val species: Species =
                if (frag.nodes.size == 1) Species.Elemental(frag.nodes.single().isotope) else Species.Molecular(frag)
            return@mapIndexed { entityGenerator.createEntity(species, pos, molDirection, molVelocity, energyPerFragment, env, electrons) }
        }

        return ReactionOutcome(
            consumed = listOf(mol),
            spawn = spawn,
            description = "$id: ${graph.formulaPretty()} (звезда) -> " + fragments.joinToString(" + ") { it.formulaPretty() },
        )
    }
}