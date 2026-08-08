package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome

/**
 * Термическая диссоциация в звезде : молекула в горячей
 * звёздной среде рвётся по слабейшей связи КАЖДЫЙ тик — рекурсивно до атомов.
 * Дальше атомы термически ионизуются → плазма.
 *
 */
class StarDissociation(private val entityGenerator: IEntityGenerator) : MoleculeReactionRule() {
    override val id = "StarDissociation"

    private data class Match(val molecule: Molecule) : MatchedData

    override fun matchesMolecule(molecule: Molecule, neighbors: List<Entity>): MatchedData? {
        if (neighbors.isNotEmpty()) return null   // «сам с собой»
        if (molecule.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return null
        if (molecule.dissociationEnergy == null) return null   // рвать нечего (нет связей / тип не в каталоге)
        return Match(molecule)
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (mol) = match as Match
        val bond = mol.weakestBond!!                              // matches гарантировал наличие связи

        // Разрыв оплачивает тепловая ванна звезды — собственную энергию молекулы не тратим, она целиком
        // достаётся продуктам. Куда её положить и кольцо это или распад — забота breakBond.
        return breakBond(mol, bond, entityGenerator, energyToShare = mol.energy)
    }
}