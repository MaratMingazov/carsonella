package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome

/**
 * Термическая диссоциация в звезде (§6 docs/molecule-graph.md, «распад по среде»): молекула в горячей
 * звёздной среде рвётся по слабейшей связи КАЖДЫЙ тик — рекурсивно до атомов. Дальше атомы термически
 * ионизуются ([StarThermalIonization]) → плазма.
 *
 * Зеркало [StarThermalIonization] для атомов (атом в звезде теряет по электрону за тик до голого ядра):
 * триггер — САМА СРЕДА (Star), а не фотон; тепловой энергии в звезде с избытком, поэтому порога/фотона
 * НЕТ (в отличие от [PhotoDissociation]). Внутримолекулярная реакция «сам с собой» (запрос без соседей),
 * как распады/усиление: молекула зовёт себя из `Molecule.step`, когда она в звезде.
 *
 * Рекурсия — сама собой: осколок-молекула на следующем тике снова в звезде → снова рвётся, пока не
 * останутся атомы. Порядок разрыва (слабейшая связь) на финал не влияет — всё равно всё распадётся —
 * но переиспользует [Molecule.weakestBond]/[MoleculeGraph.split] (общая графовая хирургия) и физически
 * осмыслен (у слабейшей связи самый низкий барьер). Энергия осколков — доля энергии молекулы: разрыв
 * оплачивает тепловая ванна звезды (её не тратим), собственную энергию молекулы не теряем.
 */
class StarDissociation(private val entityGenerator: IEntityGenerator) : MoleculeReactionRule() {
    override val id = "StarDissociation"

    private data class Match(val molecule: Molecule) : MatchedData

    override fun matchesMolecule(subject: Molecule, neighbors: List<Entity>): MatchedData? {
        if (neighbors.isNotEmpty()) return null   // «сам с собой»
        if (!subject.state().value.alive) return null
        if (subject.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return null
        if (subject.dissociationEnergy == null) return null   // рвать нечего (нет связей / тип не в каталоге)
        return Match(subject)
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (mol) = match as Match
        val bond = mol.weakestBond!!                              // matches гарантировал наличие связи

        // Разрыв оплачивает тепловая ванна звезды — собственную энергию молекулы не тратим, она целиком
        // достаётся продуктам. Куда её положить и кольцо это или распад — забота breakBond.
        return breakBond(mol, bond, entityGenerator, energyToShare = mol.state().value.energy)
    }
}