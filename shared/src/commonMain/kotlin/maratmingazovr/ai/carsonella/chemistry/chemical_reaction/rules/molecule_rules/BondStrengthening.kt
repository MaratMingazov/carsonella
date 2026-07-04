package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.graph.Bond
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

    private var molecule: Entity<*>? = null
    private var bond: Bond? = null

    override fun matchesMolecule(reagents: List<Entity<*>>): Boolean {
        molecule = null
        bond = null
        if (reagents.size != 1) return false   // как распады: только «сам с собой», без соседей
        val first = reagents.first()
        if (!first.state().value.alive) return false
        if (first.getEnvironment().getEnvTemperature() == TemperatureMode.Star) return false   // в звезде молекул нет
        val graph = (first.state().value.species as Species.Molecular).graph
        val candidate = graph.strengthenableBonds().firstOrNull() ?: return false
        molecule = first
        bond = candidate
        return true
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {
        val mol = molecule!!
        val b = bond!!
        val state = mol.state().value
        val graph = (state.species as Species.Molecular).graph
        val strengthened = graph.strengthenBond(b.atom1, b.atom2)
        val env = mol.getEnvironment()

        // Усиление ЭКЗОТЕРМИЧНО: высвобождаем прирост энергии связи E(k+1)−E(k) фотоном (как при образовании).
        val isoA = graph.nodes.first { it.localId == b.atom1 }.isotope
        val isoB = graph.nodes.first { it.localId == b.atom2 }.isotope
        val hi = BondEnergy.of(isoA, isoB, b.order + 1)
        val lo = BondEnergy.of(isoA, isoB, b.order)
        val released = if (hi != null && lo != null) hi - lo else null

        val spawn = mutableListOf<() -> Entity<*>>(
            { entityGenerator.createEntity(Species.Molecular(strengthened), state.position, state.direction, state.velocity, state.energy, env, state.electrons) },
        )
        if (released != null && released > 0f) {
            spawn += {
                entityGenerator.createEntity(Element.PHOTON, state.position, randomDirection(entityGenerator.random), 10f, energy = released, environment = env, electrons = 0)
            }
        }

        return ReactionOutcome(
            consumed = listOf(mol),
            spawn = spawn,
            description = "$id: ${graph.formulaPretty()} связь ${b.atom1}-${b.atom2} ${b.order}→${b.order + 1}" +
                (released?.let { " + γ[${it}eV]" } ?: ""),
        )
    }
}