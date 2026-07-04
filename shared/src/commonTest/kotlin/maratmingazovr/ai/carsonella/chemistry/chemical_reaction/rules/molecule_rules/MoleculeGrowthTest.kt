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
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Шаг 3b: рост молекулы. ·OH + H → H₂O (атом+молекула) и ·OH + ·OH → H₂O₂ (молекула+молекула)
 * end-to-end (matchesMolecule → produce → spawn → слитый граф), плюс отказы.
 */
class MoleculeGrowthTest {

    private class CapturingGenerator : IEntityGenerator {
        override val random = Random(0)
        var captured: Species? = null
        var capturedElectrons: Int? = null
        override fun createEntity(
            species: Species, position: Position, direction: Vec2D,
            velocity: Float, energy: Float, environment: IEnvironment, electrons: Int,
        ): Entity<*> {
            captured = species
            capturedElectrons = electrons
            return Atom(0L, Element.HYDROGEN, position, direction, velocity, energy, electrons = 1)
        }
    }

    private val env = Environment()   // дефолт: TemperatureMode.Space
    private var nextId = 1L

    private fun atom(element: Element, x: Float, electrons: Int): Atom =
        Atom(nextId++, element, Position(x, 0f), Vec2D(0f, 0f), 0f, 0f, electrons)
            .also { it.setEnvironment(env) }

    // ·OH как сущность-молекула: O(0)–H(1), нейтральная (электронов = протонов = 9).
    private fun hydroxyl(x: Float): Molecule {
        val graph = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.OXYGEN_16), AtomNode(1, Element.HYDROGEN)),
            bonds = listOf(Bond(0, 1, order = 1)),
        )
        return Molecule(nextId++, graph, Position(x, 0f), Vec2D(0f, 0f), 0f, 0f, electrons = 9)
            .also { it.setEnvironment(env) }
    }

    // H₂O как сущность-молекула: закрытая оболочка, свободных слотов нет.
    private fun water(x: Float): Molecule {
        val graph = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.OXYGEN_16), AtomNode(1, Element.HYDROGEN), AtomNode(2, Element.HYDROGEN)),
            bonds = listOf(Bond(0, 1, order = 1), Bond(0, 2, order = 1)),
        )
        return Molecule(nextId++, graph, Position(x, 0f), Vec2D(0f, 0f), 0f, 0f, electrons = 10)
            .also { it.setEnvironment(env) }
    }

    @Test
    fun hydroxylPlusHydrogenMakesWater() {
        val gen = CapturingGenerator()
        val rule = MoleculeGrowth(gen)
        val oh = hydroxyl(0f)
        val h = atom(Element.HYDROGEN, 3f, electrons = 1)

        assertTrue(rule.matchesMolecule(listOf(oh, h)))

        val outcome = rule.produce()
        assertEquals(listOf<Entity<*>>(oh, h), outcome.consumed)

        outcome.spawn.forEach { it() }
        val species = gen.captured
        assertTrue(species is Species.Molecular)
        assertEquals("H2O", (species as Species.Molecular).graph.formula())
        assertEquals(18f, species.graph.mass())
        assertFalse(species.graph.hasFreeSlot())          // вода насыщена
        assertEquals(10, gen.capturedElectrons)           // 9 (·OH) + 1 (H) — сохранение электронов
    }

    @Test
    fun hydroxylPlusHydroxylMakesPeroxide() {
        // молекула + молекула: ·OH + ·OH → H₂O₂ (H–O–O–H).
        val gen = CapturingGenerator()
        val rule = MoleculeGrowth(gen)
        val oh1 = hydroxyl(0f)
        val oh2 = hydroxyl(3f)

        assertTrue(rule.matchesMolecule(listOf(oh1, oh2)))
        rule.produce().spawn.forEach { it() }

        val species = gen.captured as Species.Molecular
        assertEquals("H2O2", species.graph.formula())
        assertEquals(34f, species.graph.mass())
        assertEquals(18, gen.capturedElectrons)           // 9 + 9
    }

    @Test
    fun saturatedMoleculeDoesNotGrow() {
        // У воды нет свободных слотов — расти некуда, даже если рядом есть H.
        val rule = MoleculeGrowth(CapturingGenerator())
        assertFalse(rule.matchesMolecule(listOf(water(0f), atom(Element.HYDROGEN, 3f, electrons = 1))))
    }

    @Test
    fun farApartDoesNotGrow() {
        val rule = MoleculeGrowth(CapturingGenerator())
        assertFalse(rule.matchesMolecule(listOf(hydroxyl(0f), atom(Element.HYDROGEN, 1000f, electrons = 1))))
    }

    @Test
    fun ionizedAtomPartnerRejected() {
        // ион H⁺ (нет электронов для общей пары) — не годится в партнёры.
        val rule = MoleculeGrowth(CapturingGenerator())
        assertFalse(rule.matchesMolecule(listOf(hydroxyl(0f), atom(Element.HYDROGEN, 3f, electrons = 0))))
    }
}