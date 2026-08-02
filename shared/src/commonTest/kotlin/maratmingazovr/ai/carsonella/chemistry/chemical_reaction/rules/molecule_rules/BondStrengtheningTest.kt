package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.Environment
import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.ReactionSelection
import maratmingazovr.ai.carsonella.chemistry.graph.AtomNode
import maratmingazovr.ai.carsonella.chemistry.graph.Bond
import maratmingazovr.ai.carsonella.chemistry.graph.BondEnergy
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Шаг 3c: усиление связи — ТОЛЬКО по клику игрока. O–O усиливается в O=O, N–N → N=N; связь приезжает
 * выбором игрока ([ReactionSelection.StrengthenBond]), само правило её не выбирает. Чужой выбор и
 * не-self-запрос отсекаются.
 */
class BondStrengtheningTest {

    private class CapturingGenerator : IEntityGenerator {
        override val random = Random(0)
        data class Spawned(val species: Species, val energy: Float, val electrons: Int)
        val spawned = mutableListOf<Spawned>()
        override fun createEntity(
            species: Species, position: Position, direction: Vec2D,
            velocity: Float, energy: Float, environment: IEnvironment, electrons: Int,
        ): Entity {
            spawned += Spawned(species, energy, electrons)
            return Atom(0L, Element.HYDROGEN, position, direction, velocity, energy = 0f, electrons = 1)
        }
    }

    private val env = Environment()   // дефолт: TemperatureMode.Space
    private var nextId = 1L

    private fun diatomic(el: Element, order: Int) = MoleculeGraph(
        nodes = listOf(AtomNode(0, el), AtomNode(1, el)),
        bonds = listOf(Bond(0, 1, order = order)),
    )

    private fun molecule(graph: MoleculeGraph, electrons: Int): Molecule =
        Molecule(nextId++, graph, Position(0f, 0f), Vec2D(0f, 0f), 0f, 0f, electrons)
            .also { it.setEnvironment(env) }

    // Выбор игрока: связь берём из кандидатов молекулы — ровно как это делает UI.
    private fun clickOnFirstStrengthenableBond(mol: Molecule): ReactionSelection.StrengthenBond {
        val state = mol.state().value
        val bonds = (state.species as Species.Molecular).strengthenableBonds(state.centerPosition)
        return ReactionSelection.StrengthenBond(bonds.first())
    }

    @Test
    fun isolatedOxygenPairStrengthensToDouble() {
        val gen = CapturingGenerator()
        val rule = BondStrengthening(gen)
        val oo = molecule(diatomic(Element.OXYGEN_16, order = 1), electrons = 16)   // O–O, нейтральный

        val match = assertNotNull(rule.matches(listOf(oo), clickOnFirstStrengthenableBond(oo)))
        val outcome = rule.produce(match)
        assertEquals(listOf<Entity>(oo), outcome.consumed)

        outcome.spawn.forEach { it() }
        val product = gen.spawned.single { it.species is Species.Molecular }
        val graph = (product.species as Species.Molecular).graph
        assertEquals("O2", graph.formula)
        assertEquals(2, graph.bonds.single().order)     // O–O → O=O
        assertFalse(graph.hasFreeValence)                // O=O насыщен
        assertEquals(16, product.electrons)             // электроны сохранены

        // усиление экзотермично → фотон на прирост энергии связи E(O=O) − E(O–O)
        val photon = gen.spawned.single { (it.species as? Species.Atomic)?.element == Element.PHOTON }
        assertEquals(
            BondEnergy.of(Element.OXYGEN_16, Element.OXYGEN_16, 2)!! - BondEnergy.of(Element.OXYGEN_16, Element.OXYGEN_16, 1)!!,
            photon.energy,
        )
    }

    @Test
    fun requiresExactlyOneReagent() {
        val rule = BondStrengthening(CapturingGenerator())
        val oo = molecule(diatomic(Element.OXYGEN_16, 1), electrons = 16)
        val extra = molecule(diatomic(Element.OXYGEN_16, 1), electrons = 16)
        // size != 1 — форс приходит self-запросом (World.requestMoleculeAction), соседей тут быть не может
        assertNull(rule.matches(listOf(oo, extra), clickOnFirstStrengthenableBond(oo)))
    }

    @Test
    fun foreignSelectionIsNotOurs() {
        // Правило обслуживает ТОЛЬКО свой выбор: клик «замкнуть кольцо» — не его дело.
        val rule = BondStrengthening(CapturingGenerator())
        val oo = molecule(diatomic(Element.OXYGEN_16, 1), electrons = 16)
        assertNull(rule.matches(listOf(oo), ReactionSelection.CloseRing))
    }

    @Test
    fun nitrogenStrengthensStepwise() {
        val gen = CapturingGenerator()
        val rule = BondStrengthening(gen)
        val nn = molecule(diatomic(Element.NITROGEN_14, order = 1), electrons = 14)   // N–N

        val match = assertNotNull(rule.matches(listOf(nn), clickOnFirstStrengthenableBond(nn)))
        rule.produce(match).spawn.forEach { it() }
        val graph = (gen.spawned.single { it.species is Species.Molecular }.species as Species.Molecular).graph
        assertEquals(2, graph.bonds.single().order)   // N–N → N=N (до N≡N — ещё один тик)
    }
}