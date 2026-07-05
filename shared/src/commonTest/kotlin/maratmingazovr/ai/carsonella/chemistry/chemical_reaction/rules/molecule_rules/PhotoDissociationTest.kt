package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.Environment
import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.SubAtom
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
 * Граф-фотодиссоциация: фотон рвёт молекулу по слабейшей связи. H₂O + γ → ·OH + H·,
 * слабейшая связь первой (перекись рвётся по O–O), избыток энергии на осколках, отказы.
 */
class PhotoDissociationTest {

    private class CapturingGenerator : IEntityGenerator {
        override val random = Random(0)
        data class Spawned(val species: Species, val energy: Float, val electrons: Int)
        val spawned = mutableListOf<Spawned>()
        override fun createEntity(
            species: Species, position: Position, direction: Vec2D,
            velocity: Float, energy: Float, environment: IEnvironment, electrons: Int,
        ): Entity<*> {
            spawned += Spawned(species, energy, electrons)
            return Atom(0L, Element.HYDROGEN, position, direction, velocity, energy, electrons = 1)
        }
    }

    private val env = Environment()   // дефолт: TemperatureMode.Space
    private var nextId = 1L

    // Фотон-сущность (SubAtom с Element.PHOTON), в позиции молекулы → заведомо в радиусе активации.
    private fun photon(energy: Float, x: Float = 0f): SubAtom =
        SubAtom(nextId++, Element.PHOTON, Position(x, 0f), Vec2D(1f, 0f), velocity = 10f, energy = energy, electrons = 0)
            .also { it.setEnvironment(env) }

    // H₂O: O(0)–H(1), O(0)–H(2), нейтральная (electrons = protons = 10). Обе связи O–H (4.80 эВ).
    private fun water(): Molecule {
        val graph = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.OXYGEN_16), AtomNode(1, Element.HYDROGEN), AtomNode(2, Element.HYDROGEN)),
            bonds = listOf(Bond(0, 1, order = 1), Bond(0, 2, order = 1)),
        )
        return Molecule(nextId++, graph, Position(0f, 0f), Vec2D(0f, 0f), 0f, 0f, electrons = 10)
            .also { it.setEnvironment(env) }
    }

    // H₂O₂ (перекись): H(0)–O(1)–O(2)–H(3), нейтральная (electrons = 18). Слабейшая связь — O–O (1.51 эВ).
    private fun peroxide(): Molecule {
        val graph = MoleculeGraph(
            nodes = listOf(
                AtomNode(0, Element.HYDROGEN), AtomNode(1, Element.OXYGEN_16),
                AtomNode(2, Element.OXYGEN_16), AtomNode(3, Element.HYDROGEN),
            ),
            bonds = listOf(Bond(0, 1, order = 1), Bond(1, 2, order = 1), Bond(2, 3, order = 1)),
        )
        return Molecule(nextId++, graph, Position(0f, 0f), Vec2D(0f, 0f), 0f, 0f, electrons = 18)
            .also { it.setEnvironment(env) }
    }

    @Test
    fun photonAboveThresholdSplitsWaterIntoHydroxylAndHydrogen() {
        val gen = CapturingGenerator()
        val rule = PhotoDissociation(gen)
        val w = water()
        val ohBond = BondEnergy.of(Element.OXYGEN_16, Element.HYDROGEN, 1)!!   // 4.80
        val ph = photon(energy = ohBond + 2f)                                  // с запасом над порогом

        assertTrue(rule.matchesMolecule(listOf(w, ph)))
        val outcome = rule.produce()
        assertEquals(listOf<Entity<*>>(ph, w), outcome.consumed)               // потребляются фотон и молекула
        outcome.spawn.forEach { it() }

        assertEquals(2, gen.spawned.size)
        val oh = gen.spawned.single { it.species is Species.Molecular }
        val h = gen.spawned.single { it.species is Species.Elemental }
        assertEquals("HO", (oh.species as Species.Molecular).graph.formula())  // ·OH
        assertEquals(Element.HYDROGEN, (h.species as Species.Elemental).element)
        assertEquals(10, oh.electrons + h.electrons)                           // электроны сохранены (9 + 1)
        assertEquals(9, oh.electrons)
        assertEquals(1, h.electrons)

        // избыток (доступная − порог) не потерян: разложен в energy осколков поровну
        assertEquals((ohBond + 2f) - ohBond, gen.spawned.sumOf { it.energy.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun photonBelowThresholdDoesNotDissociate() {
        val rule = PhotoDissociation(CapturingGenerator())
        val ohBond = BondEnergy.of(Element.OXYGEN_16, Element.HYDROGEN, 1)!!
        assertFalse(rule.matchesMolecule(listOf(water(), photon(energy = ohBond - 1f))))   // фотон слабее порога
    }

    @Test
    fun weakestBondBreaksFirstInPeroxide() {
        // Порог O–O (1.51) < O–H (4.80). Фотон 2 эВ рвёт именно O–O → два ·OH, а не O–H.
        val gen = CapturingGenerator()
        val rule = PhotoDissociation(gen)
        assertTrue(rule.matchesMolecule(listOf(peroxide(), photon(energy = 2f))))
        rule.produce().spawn.forEach { it() }

        assertEquals(2, gen.spawned.size)
        assertTrue(gen.spawned.all { it.species is Species.Molecular })            // оба осколка — молекулы ·OH
        gen.spawned.forEach { assertEquals("HO", (it.species as Species.Molecular).graph.formula()) }
        assertEquals(18, gen.spawned.sumOf { it.electrons })                       // 9 + 9
    }

    @Test
    fun noPhotonNoDissociation() {
        // Рядом атом H, но фотона нет → распад не запускается.
        val rule = PhotoDissociation(CapturingGenerator())
        val h = Atom(nextId++, Element.HYDROGEN, Position(1f, 0f), Vec2D(0f, 0f), 0f, 0f, electrons = 1)
            .also { it.setEnvironment(env) }
        assertFalse(rule.matchesMolecule(listOf(water(), h)))
    }

    @Test
    fun farPhotonDoesNotDissociate() {
        // Фотон за пределами радиуса активации (MOLECULE_RADIUS = 20) не рвёт молекулу.
        val rule = PhotoDissociation(CapturingGenerator())
        assertFalse(rule.matchesMolecule(listOf(water(), photon(energy = 10f, x = 1000f))))
    }
}