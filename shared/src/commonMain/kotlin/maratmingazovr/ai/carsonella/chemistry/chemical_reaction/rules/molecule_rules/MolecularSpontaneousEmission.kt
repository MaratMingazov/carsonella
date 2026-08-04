package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StateUpdate
import maratmingazovr.ai.carsonella.randomDirection

/**
 * Спонтанный сброс внутренней энергии молекулы — зеркало атомного
 * [maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.SpontaneousEmission].
 * Закрывает асимметрию: у атома путь «остыть» был (спуск по уровням + фотон), у молекулы — нет, и
 * энергия осколка после распада застревала навсегда. Две ветки по величине энергии:
 *
 *  1. ПРЕДИССОЦИАЦИЯ (`energy ≥` порог слабейшей связи): молекула распадается САМА — своя внутренняя
 *     энергия оплачивает разрыв (зеркало [PhotoDissociation], но БЕЗ фотона). Физически честно:
 *     молекула с колебательной энергией выше энергии связи разваливается (unimolecular dissociation).
 *     Порог «тратится» на разрыв, избыток делится по осколкам через [spawnFragments].
 *
 *  2. ИЗЛУЧЕНИЕ (`0 < energy <` порог): избыток уходит ОДНИМ фотоном, молекула → в основное состояние
 *     (`energy = 0`). УПРОЩЕНИЕ: реально молекула сбрасывала бы энергию серией ИК-квантов (колебательная
 *     релаксация) или в столкновениях; «один фотон» честно сохраняет энергию, но завышает его «цвет»
 *     (нет колебательной лестницы, куда спускаться — см. [MoleculeGraph.energyLevels]). Стохастично
 *     (`chance`, как у атома): остывание плавное, и остаётся «окно каскада» — горячий осколок ещё может
 *     успеть распустить/ионизовать соседа, прежде чем остынет.
 *
 * Гейт по среде: в звезде распадом рулит [StarDissociation] (каждый тик, безусловно) — там не вмешиваемся.
 * weight = 0: созидание (рост/усиление/кольцо — положительный weight) выигрывает, а сброс энергии
 * срабатывает, когда строить нечего. Фотон вылетает со скоростью 40 в случайном направлении (как в
 * атомном SpontaneousEmission) — за тик покидает радиус активации, чтобы не переионизовать сам источник.
 */
class MolecularSpontaneousEmission(private val entityGenerator: IEntityGenerator) : MoleculeReactionRule() {
    override val id = "MolecularSpontaneousEmission"

    /** [dissociationThreshold] не-null → ветка предиссоциации (распад); null → ветка излучения фотона. */
    private data class Match(val molecule: Molecule, val dissociationThreshold: Float?) : MatchedData

    override fun matchesMolecule(subject: Molecule, neighbors: List<Entity>): MatchedData? {
        if (neighbors.isNotEmpty()) return null      // «сам с собой»

        val subjectState = subject.state().value
        if (!subjectState.alive) return null
        if (subjectState.energy <= 0f) return null              // остывать нечего
        if (subject.getEnvironment().getEnvTemperature() == TemperatureMode.Star) return null  // в звезде — StarDissociation

        val threshold = subject.dissociationEnergy

        // Ветка 1 — предиссоциация: энергии хватает разорвать слабейшую связь → распад (срабатывает всегда).
        if (threshold != null && subjectState.energy >= threshold) {
            return Match(subject, threshold)
        }

        // Ветка 2 — излучение: избыток ниже порога распада, сбрасываем фотоном. Постепенно (chance), как атом.
        if (!chance(0.02f, entityGenerator.random)) return null
        return Match(subject, dissociationThreshold = null)
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (molecule, dissociationThreshold) = match as Match
        val moleculeState = molecule.state().value

        if (dissociationThreshold != null) {
            // Ветка 1: предиссоциация — своя энергия платит за разрыв слабейшей связи (зеркало
            // PhotoDissociation без фотона). Порог «тратится», избыток делится по осколкам.
            val bond = molecule.weakestBond!!
            val fragments = molecule.graph.split(bond.atom1.structure.localId, bond.atom2.structure.localId)
            val excessPerFragment = (moleculeState.energy - dissociationThreshold).coerceAtLeast(0f) / fragments.size
            return ReactionOutcome(
                consumed = listOf(molecule),
                spawn = spawnFragments(fragments, molecule, entityGenerator, excessPerFragment),
            )
        }

        // Ветка 2: излучение — вся внутренняя энергия уходит одним фотоном, молекула → energy = 0.
        val photonEnergy = moleculeState.energy
        val env = molecule.getEnvironment()
        return ReactionOutcome(
            updateState = listOf(StateUpdate(molecule) { molecule.setEnergy(0f) }),
            spawn = listOf {
                entityGenerator.createEntity(
                    Element.PHOTON,
                    moleculeState.kinematics.position.plus(Position(molecule.radius, 0f)),
                    randomDirection(entityGenerator.random),
                    MAX_VELOCITY,
                    energy = photonEnergy,
                    environment = env,
                    electrons = 0,
                )
            },
        )
    }
}