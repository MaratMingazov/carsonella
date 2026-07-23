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
import maratmingazovr.ai.carsonella.chemistry.graph.AtomNode
import maratmingazovr.ai.carsonella.chemistry.graph.Bond
import maratmingazovr.ai.carsonella.chemistry.graph.BondEnergy
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Шаг 3c: усиление связи. Изолированная O–O усиливается в O=O (эмёрджентный O₂), N–N → N=N;
 * работает только по одному реагенту (само-реакция), насыщенные молекулы не усиливаются.
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
            return Atom(0L, Element.HYDROGEN, position, direction, velocity, energy, electrons = 1)
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

    @Test
    fun isolatedOxygenPairStrengthensToDouble() {
        val gen = CapturingGenerator()
        val rule = BondStrengthening(gen)
        val oo = molecule(diatomic(Element.OXYGEN_16, order = 1), electrons = 16)   // O–O, нейтральный

        assertTrue(rule.matchesMolecule(listOf(oo)))
        val outcome = rule.produce()
        assertEquals(listOf<Entity>(oo), outcome.consumed)

        outcome.spawn.forEach { it() }
        val product = gen.spawned.single { it.species is Species.Molecular }
        val graph = (product.species as Species.Molecular).graph
        assertEquals("O2", graph.formula)
        assertEquals(2, graph.bonds.single().order)     // O–O → O=O
        assertFalse(graph.hasFreeSlot)                // O=O насыщен
        assertEquals(16, product.electrons)             // электроны сохранены

        // усиление экзотермично → фотон на прирост энергии связи E(O=O) − E(O–O)
        val photon = gen.spawned.single { (it.species as? Species.Elemental)?.element == Element.PHOTON }
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
        assertFalse(rule.matchesMolecule(listOf(oo, extra)))   // size != 1 — усиление только «сам с собой»
    }

    @Test
    fun saturatedMoleculeDoesNotStrengthen() {
        val rule = BondStrengthening(CapturingGenerator())
        val o2 = molecule(diatomic(Element.OXYGEN_16, order = 2), electrons = 16)   // O=O — оба O насыщены
        assertFalse(rule.matchesMolecule(listOf(o2)))
    }

    @Test
    fun nitrogenStrengthensStepwise() {
        val gen = CapturingGenerator()
        val rule = BondStrengthening(gen)
        val nn = molecule(diatomic(Element.NITROGEN_14, order = 1), electrons = 14)   // N–N

        assertTrue(rule.matchesMolecule(listOf(nn)))
        rule.produce().spawn.forEach { it() }
        val graph = (gen.spawned.single { it.species is Species.Molecular }.species as Species.Molecular).graph
        assertEquals(2, graph.bonds.single().order)   // N–N → N=N (до N≡N — ещё один тик)
    }
}