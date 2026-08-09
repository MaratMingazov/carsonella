package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.bondPhoton
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StateUpdate

// Спонтанный сброс внутренней энергии молекулы
class MolecularSpontaneousEmission(private val entityGenerator: IEntityGenerator) : MoleculeReactionRule() {
    override val id = "MolecularSpontaneousEmission"

    /** [dissociationThreshold] не-null → ветка предиссоциации (распад); null → ветка излучения фотона. */
    private data class Match(val molecule: Molecule, val dissociationThreshold: Float?) : MatchedData

    override fun matchesMolecule(molecule: Molecule, neighbors: List<Entity>): MatchedData? {
        if (neighbors.isNotEmpty()) return null      // «сам с собой»

        if (molecule.energy <= 0f) return null              // остывать нечего
        if (molecule.getEnvironment().getEnvTemperature() == TemperatureMode.Star) return null  // в звезде — StarDissociation

        val threshold = molecule.dissociationEnergy

        // Ветка 1 — предиссоциация: энергии хватает разорвать слабейшую связь → распад (срабатывает всегда).
        if (threshold != null && molecule.energy >= threshold) {
            return Match(molecule, threshold)
        }

        // Ветка 2 — излучение: избыток ниже порога распада, сбрасываем фотоном. Постепенно (chance), как атом.
        if (!chance(0.02f, entityGenerator.random)) return null
        return Match(molecule, dissociationThreshold = null)
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (molecule, dissociationThreshold) = match as Match

        if (dissociationThreshold != null) {
            // Ветка 1: предиссоциация — своя энергия платит за разрыв слабейшей связи (зеркало
            // PhotoDissociation без фотона). Порог «тратится», избыток достаётся продуктам.
            return breakBond(
                molecule,
                molecule.weakestBond!!,
                entityGenerator,
                energyToShare = molecule.energy - dissociationThreshold,
            )
        }

        // Ветка 2: излучение — вся внутренняя энергия уходит одним фотоном, молекула → energy = 0.
        val photonEnergy = molecule.energy
        val env = molecule.getEnvironment()
        // Излучает СЛУЧАЙНАЯ связь: внутренняя энергия у нас живёт в пружинах связей, на связи фотон
        // и рождается (см. bondPhoton).
        val bond = molecule.bonds.random(entityGenerator.random)
        val atom1 = molecule.atom(bond.localId1)
        val atom2 = molecule.atom(bond.localId2)
        val photon = bondPhoton(
            atom1.kinematics.position, atom1.radius,
            atom2.kinematics.position, atom2.radius,
            entityGenerator.random,
        )

        return ReactionOutcome(
            updateState = listOf(StateUpdate(molecule) { molecule.energy = 0f }),
            spawn = listOf {
                entityGenerator.createEntity(
                    Element.PHOTON,
                    photon.position,
                    photon.direction,
                    MAX_VELOCITY,
                    energy = photonEnergy,
                    environment = env,
                    electrons = 0,
                )
            },
        )
    }
}