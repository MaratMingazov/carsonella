package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.Environment
import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.graph.AtomNode
import maratmingazovr.ai.carsonella.chemistry.graph.Bond
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Термическая диссоциация в звезде: молекула в Star-среде рвёт слабейшую связь за тик, рекурсивно до
 * атомов. H₂O → ·OH + H → (·OH снова) → O + H. В космосе не срабатывает.
 */
class StarDissociationTest {

    private class CapturingGenerator : IEntityGenerator {
        override val random = Random(0)
        data class Spawned(val species: Species, val electrons: Int)
        val spawned = mutableListOf<Spawned>()
        override fun createEntity(
            species: Species, position: Position, direction: Vec2D,
            velocity: Float, energy: Float, environment: IEnvironment, electrons: Int,
        ): Entity<*> {
            spawned += Spawned(species, electrons)
            return Atom(0L, Element.HYDROGEN, position, direction, velocity, energy, electrons = 1)
        }
    }

    private val star = Environment(temperature = TemperatureMode.Star)
    private val space = Environment()   // дефолт: TemperatureMode.Space
    private var nextId = 1L

    private fun water(inEnv: IEnvironment): Molecule {
        val graph = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.OXYGEN_16), AtomNode(1, Element.HYDROGEN), AtomNode(2, Element.HYDROGEN)),
            bonds = listOf(Bond(0, 1, order = 1), Bond(0, 2, order = 1)),
        )
        return Molecule(nextId++, graph, Position(0f, 0f), Vec2D(0f, 0f), 0f, 0f, electrons = 10)
            .also { it.setEnvironment(inEnv) }
    }

    private fun hydroxyl(inEnv: IEnvironment): Molecule {
        val graph = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.OXYGEN_16), AtomNode(1, Element.HYDROGEN)),
            bonds = listOf(Bond(0, 1, order = 1)),
        )
        return Molecule(nextId++, graph, Position(0f, 0f), Vec2D(0f, 0f), 0f, 0f, electrons = 9)
            .also { it.setEnvironment(inEnv) }
    }

    @Test
    fun waterInStarSplitsIntoHydroxylAndHydrogen() {
        // Первый шаг рекурсии: H₂O рвёт слабейшую связь (O–H) → ·OH + H. Ни фотона, ни порога.
        val gen = CapturingGenerator()
        val rule = StarDissociation(gen)
        val w = water(star)

        assertTrue(rule.matchesMolecule(listOf(w)))          // «сам с собой», без соседей и фотона
        val outcome = rule.produce()
        assertEquals(listOf<Entity<*>>(w), outcome.consumed)
        outcome.spawn.forEach { it() }

        assertEquals(2, gen.spawned.size)
        val oh = gen.spawned.single { it.species is Species.Molecular }
        val h = gen.spawned.single { it.species is Species.Elemental }
        assertEquals("HO", (oh.species as Species.Molecular).graph.formula)
        assertEquals(Element.HYDROGEN, (h.species as Species.Elemental).element)
        assertEquals(10, oh.electrons + h.electrons)          // электроны сохранены (9 + 1)
    }

    @Test
    fun hydroxylInStarSplitsIntoTwoAtoms() {
        // Второй шаг рекурсии: осколок ·OH снова в звезде → распадается до атомов O + H.
        val gen = CapturingGenerator()
        val rule = StarDissociation(gen)
        assertTrue(rule.matchesMolecule(listOf(hydroxyl(star))))
        rule.produce().spawn.forEach { it() }

        assertEquals(2, gen.spawned.size)
        assertTrue(gen.spawned.all { it.species is Species.Elemental })   // оба осколка — атомы
        assertEquals(
            setOf(Element.OXYGEN_16, Element.HYDROGEN),
            gen.spawned.map { (it.species as Species.Elemental).element }.toSet(),
        )
        assertEquals(9, gen.spawned.sumOf { it.electrons })              // 8 (O) + 1 (H)
    }

    @Test
    fun moleculeInSpaceDoesNotThermallyDissociate() {
        // В космосе (не звезда) термического распада нет — молекула стабильна.
        val rule = StarDissociation(CapturingGenerator())
        assertFalse(rule.matchesMolecule(listOf(water(space))))
    }

    @Test
    fun onlyMatchesSoloReagent() {
        // Как распады/усиление: только «сам с собой». С соседями (size != 1) — не матчится.
        val rule = StarDissociation(CapturingGenerator())
        val neighbor = Atom(nextId++, Element.HYDROGEN, Position(1f, 0f), Vec2D(0f, 0f), 0f, 0f, electrons = 1)
            .also { it.setEnvironment(star) }
        assertFalse(rule.matchesMolecule(listOf(water(star), neighbor)))
    }
}