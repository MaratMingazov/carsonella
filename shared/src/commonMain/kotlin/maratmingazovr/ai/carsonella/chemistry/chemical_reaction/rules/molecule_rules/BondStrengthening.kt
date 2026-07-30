package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
import maratmingazovr.ai.carsonella.chemistry.MolecularBond
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.graph.BondEnergy
import maratmingazovr.ai.carsonella.randomDirection

/**
 * Усиление связи (§6, Шаг 3c): у молекулы связь между двумя НЕнасыщенными атомами усиливается
 * 1→2→3 (O–O → O=O, N–N → N=N → N≡N). Так эмёрджентно рождаются кратные связи, когда рост новым
 * партнёром недоступен.
 *
 * Внутримолекулярная реакция: работает по ОДНОМУ реагенту (как распады — гейт `reagents.size != 1`).
 * Молекула шлёт `requestReaction(listOf(this))` из `step()`, когда ей есть что усилить (см. Molecule.step).
 * Так усиление отделено от роста: рост — на запросах `listOf(this)+соседи` (partner-first).
 *
 * [weight] пока 0 — энергетический выбор «рост vs усиление» (сравнение выигрышей E из BondEnergy)
 * адаптируем позже; сейчас усиление просто срабатывает, когда молекула запросила его в одиночку.
 */
class BondStrengthening(
    private val entityGenerator: IEntityGenerator,
) : MoleculeReactionRule() {
    override val id = "BondStrengthening"

    private data class Match(val molecule: Entity, val bond: MolecularBond) : MatchedData

    override fun matchesMolecule(reagents: List<Entity>): MatchedData? {
        if (reagents.size != 1) return null   // как распады: только «сам с собой», без соседей
        val first = reagents.first()
        val state = first.state().value
        if (!state.alive) return null
        if (first.getEnvironment().getEnvTemperature() == TemperatureMode.Star) return null   // в звезде молекул нет
        val molecule = state.species as Species.Molecular
        val candidate = molecule.strengthenableBonds(state.position).firstOrNull() ?: return null
        return Match(first, candidate)
    }

    // Прирост энергии связи E(k+1) − E(k) — экзотермично, «+» (контракт weight = энергия реакции со
    // знаком). Сравнивается с ростом в одном resolve(): у кислорода усиление O=O (3.65) бьёт рост O–O
    // (1.51) → O₂, у углерода рост (C–H 4.28) бьёт усиление C=C (2.77) → цепи. Данные из Match.
    override fun weight(match: MatchedData): Float {
        val (_, b) = match as Match
        val hi = BondEnergy.of(b.atom1.isotope, b.atom2.isotope, b.order + 1) ?: return 0f
        val lo = BondEnergy.of(b.atom1.isotope, b.atom2.isotope, b.order) ?: return 0f
        return hi - lo
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (mol, b) = match as Match
        val state = mol.state().value
        val molecule = state.species as Species.Molecular
        val moleculeElectrons = state.electrons
        val strengthened = molecule.strengthenBond(b)
        val env = mol.getEnvironment()

        // Усиление ЭКЗОТЕРМИЧНО: высвобождаем прирост энергии связи E(k+1)−E(k) фотоном (как при образовании).
        val hi = BondEnergy.of(b.atom1.isotope, b.atom2.isotope, b.order + 1)
        val lo = BondEnergy.of(b.atom1.isotope, b.atom2.isotope, b.order)
        val released = if (hi != null && lo != null) hi - lo else null

        val spawn = mutableListOf(
            { entityGenerator.createEntity(strengthened, state.position, state.direction, state.velocity, state.energy, env, state.electrons) },
        )
        if (released != null && released > 0f) {
            spawn += {
                // Фотон уносит прирост энергии связи и УЛЕТАЕТ (скорость 40, как в SpontaneousEmission): за тик
                // покидает радиус активации, иначе PhotoDissociation мог бы поймать его и распустить молекулу —
                // тот же цикл образование↔распад, что и при росте/образовании связи.
                entityGenerator.createEntity(Element.PHOTON, state.position, randomDirection(entityGenerator.random),
                    MAX_VELOCITY, energy = released, environment = env, electrons = 0)
            }
        }

        return ReactionOutcome(
            consumed = listOf(mol),
            spawn = spawn,
            description = "$id: ${molecule.displaySymbol(moleculeElectrons)} связь ${b.atom1.localId}-${b.atom2.localId} ${b.order}→${b.order + 1}" +
                (released?.let { " + γ[${it}eV]" } ?: ""),
        )
    }
}