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
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Замыкание кольца: цепь ненасыщенных атомов сворачивается в цикл. Пол размера = 5 (напряжённые 3–4
 * не предлагаются), выбор среди 5+/6+ по weight (ringStrain), насыщенная молекула не замыкается.
 */
class RingClosureTest {

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

    // Цепь из n углеродов C0–C1–…–C(n-1), одинарные связи. Все C ненасыщены (валентность 4, в цепи занято ≤2).
    private fun carbonChain(n: Int): Molecule {
        val nodes = (0 until n).map { AtomNode(it, Element.CARBON_12) }
        val bonds = (0 until n - 1).map { Bond(it, it + 1, order = 1) }
        val graph = MoleculeGraph(nodes, bonds)
        return Molecule(nextId++, graph, Position(0f, 0f), Vec2D(0f, 0f), 0f, 0f, electrons = graph.protons)
            .also { it.setEnvironment(env) }
    }

    @Test
    fun chainOfThreeHasNoCandidates_floorAtFive() {
        // Тройка C–C–C: концы на пути 2 → кольцо 3 < 5 → НЕ предлагаем (иначе преждевременный циклопропан).
        assertTrue(carbonChain(3).graph.ringClosureCandidates.isEmpty())
        assertTrue(carbonChain(4).graph.ringClosureCandidates.isEmpty())
    }

    @Test
    fun chainOfFiveClosesIntoFiveRing() {
        val gen = CapturingGenerator()
        val rule = RingClosure(gen)
        val chain = carbonChain(5)   // концы C0–C4 на пути 4 → кольцо 5

        val match = assertNotNull(rule.matches(listOf(chain), ReactionSelection.CloseRing))
        val outcome = rule.produce(match)
        assertEquals(listOf<Entity>(chain), outcome.consumed)   // старая молекула гибнет, спавнится замкнутая

        outcome.spawn.forEach { it() }
        val ring = gen.spawned.single { it.species is Species.Molecular }
        val ringGraph = (ring.species as Species.Molecular).graph
        assertEquals(5, ringGraph.nodes.size)
        assertEquals(5, ringGraph.bonds.size)                     // цепь имела 4 связи, +1 замыкающая = цикл
        assertTrue(ringGraph.ringClosureCandidates.isEmpty())     // после замыкания концов больше нет
        // Экзотермично: вылетел фотон с нетто-энергией (C–C 3.59 − напряжение 5-кольца 0.29 ≈ 3.30 эВ).
        val photon = gen.spawned.single { (it.species as? Species.Atomic)?.element == Element.PHOTON }
        assertEquals(3.59f - 0.29f, photon.energy, 0.001f)
    }

    @Test
    fun choosesLessStrainedRingAmongCandidates() {
        // В цепочке из 6 углеродов есть и 5-кольца (C0–C4, C1–C5), и 6-кольцо (C0–C5). Правило берёт менее
        // напряжённое: при одной и той же связи C–C кольцо 6 (strain 0.0) выгоднее 5 (strain 0.29).
        val gen = CapturingGenerator()
        val rule = RingClosure(gen)
        val outcome = rule.produce(assertNotNull(rule.matches(listOf(carbonChain(6)), ReactionSelection.CloseRing)))

        assertTrue(outcome.description.contains("кольцо 6"), "ожидалось 6-кольцо, выбрано: ${outcome.description}")
        outcome.spawn.forEach { it() }
        val photon = gen.spawned.single { (it.species as? Species.Atomic)?.element == Element.PHOTON }
        assertEquals(3.59f - 0.0f, photon.energy, 0.001f)   // нетто = C–C минус напряжение 6-кольца (0)
    }

    @Test
    fun foreignSelectionIsNotOurs() {
        // Правило обслуживает ТОЛЬКО свой выбор: клик «усилить связь» — не его дело.
        val chain = carbonChain(5)
        val someBond = chain.bonds.first()
        assertNull(RingClosure(CapturingGenerator()).matches(listOf(chain), ReactionSelection.StrengthenBond(someBond)))
    }

    @Test
    fun saturatedMoleculeDoesNotClose() {
        // Вода: у H слотов нет, у O один свободный — пары со свободными слотами на пути ≥4 нет → кандидатов нет.
        val water = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.OXYGEN_16), AtomNode(1, Element.HYDROGEN), AtomNode(2, Element.HYDROGEN)),
            bonds = listOf(Bond(0, 1, 1), Bond(0, 2, 1)),
        )
        val mol = Molecule(nextId++, water, Position(0f, 0f), Vec2D(0f, 0f), 0f, 0f, electrons = 10).also { it.setEnvironment(env) }
        assertNull(RingClosure(CapturingGenerator()).matches(listOf(mol), ReactionSelection.CloseRing))
    }

    @Test
    fun requiresSelfOnlyRequest() {
        // Как усиление/распады: правило работает только на «сам с собой». Присутствие соседа → отказ.
        val chain = carbonChain(5)
        val neighbor = Atom(nextId++, Element.HYDROGEN, Position(1f, 0f), Vec2D(0f, 0f), 0f, 0f, electrons = 1).also { it.setEnvironment(env) }
        assertNull(RingClosure(CapturingGenerator()).matches(listOf(chain, neighbor), ReactionSelection.CloseRing))
    }
}