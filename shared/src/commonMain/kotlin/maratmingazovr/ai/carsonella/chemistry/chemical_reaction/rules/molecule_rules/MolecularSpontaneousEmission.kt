package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
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
    private data class Match(val molecule: Entity, val dissociationThreshold: Float?) : MatchedData

    override fun matchesMolecule(reagents: List<Entity>): MatchedData? {
        if (reagents.size != 1) return null          // «сам с собой», как усиление/кольцо/StarDissociation

        val first = reagents.first()
        val s = first.state().value
        if (!s.alive) return null
        if (s.energy <= 0f) return null              // остывать нечего
        if (first.getEnvironment().getEnvTemperature() == TemperatureMode.Star) return null  // в звезде — StarDissociation

        val graph = (s.species as Species.Molecular).graph
        val threshold = graph.weakestBondAndEnergy?.second

        // Ветка 1 — предиссоциация: энергии хватает разорвать слабейшую связь → распад (срабатывает всегда).
        if (threshold != null && s.energy >= threshold) {
            return Match(first, threshold)
        }

        // Ветка 2 — излучение: избыток ниже порога распада, сбрасываем фотоном. Постепенно (chance), как атом.
        if (!chance(0.02f, entityGenerator.random)) return null
        return Match(first, dissociationThreshold = null)
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (mol, threshold) = match as Match
        val s = mol.state().value
        val graph = (s.species as Species.Molecular).graph

        if (threshold != null) {
            // Ветка 1: предиссоциация — своя энергия платит за разрыв слабейшей связи (зеркало
            // PhotoDissociation без фотона). Порог «тратится», избыток делится по осколкам.
            val bond = graph.weakestBondAndEnergy!!.first
            val fragments = graph.split(bond.atom1, bond.atom2)
            val excessPerFragment = (s.energy - threshold).coerceAtLeast(0f) / fragments.size
            return ReactionOutcome(
                consumed = listOf(mol),
                spawn = spawnFragments(fragments, mol, entityGenerator, excessPerFragment),
                description = "$id: ${graph.formulaPretty} (E=${s.energy}eV ≥ ${threshold}eV) -> " +
                    fragments.joinToString(" + ") { it.formulaPretty },
            )
        }

        // Ветка 2: излучение — вся внутренняя энергия уходит одним фотоном, молекула → energy = 0.
        val photonEnergy = s.energy
        val env = mol.getEnvironment()
        return ReactionOutcome(
            updateState = listOf { mol.setEnergy(0f) },
            spawn = listOf {
                entityGenerator.createEntity(
                    Element.PHOTON,
                    s.centerPosition.plus(Position(s.radius, 0f)),
                    randomDirection(entityGenerator.random),
                    MAX_VELOCITY,
                    energy = photonEnergy,
                    environment = env,
                    electrons = 0,
                )
            },
            description = "$id: ${graph.formulaPretty} -> ${graph.formulaPretty} + γ[${photonEnergy}eV]",
        )
    }
}