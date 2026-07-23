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
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Молекулярная фотоионизация: фотон ≥ IP выбивает из молекулы электрон, молекула ВЫЖИВАЕТ как катион
 * (тот же граф, electrons−1), избыток уносит e⁻. Порог IP = минимум атомного IP по графу.
 */
class MolecularPhotoIonizationTest {

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

    // Фотон-сущность в позиции молекулы → заведомо в радиусе активации.
    private fun photon(energy: Float, x: Float = 0f): SubAtom =
        SubAtom(nextId++, Element.PHOTON, Position(x, 0f), Vec2D(1f, 0f), velocity = 10f, energy = energy, electrons = 0)
            .also { it.setEnvironment(env) }

    // H₂O: O(0)–H(1), O(0)–H(2), нейтральная (electrons = protons = 10). IP = min(O,H,H) = 13.6 эВ.
    private fun water(): Molecule {
        val graph = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.OXYGEN_16), AtomNode(1, Element.HYDROGEN), AtomNode(2, Element.HYDROGEN)),
            bonds = listOf(Bond(0, 1, order = 1), Bond(0, 2, order = 1)),
        )
        return Molecule(nextId++, graph, Position(0f, 0f), Vec2D(0f, 0f), 0f, 0f, electrons = 10)
            .also { it.setEnvironment(env) }
    }

    @Test
    fun ionizationEnergyIsMinAtomicIp() {
        // Вода: все атомы дают 13.6 эВ → IP = 13.6.
        assertEquals(13.6f, water().state().value.species.let { (it as Species.Molecular).graph.ionizationEnergy!! }, 0.001f)

        // CH₄: C (11.26) легче кислорода/водорода (13.6) → минимум по атомам = 11.26.
        val methane = MoleculeGraph(
            nodes = listOf(
                AtomNode(0, Element.CARBON_12), AtomNode(1, Element.HYDROGEN),
                AtomNode(2, Element.HYDROGEN), AtomNode(3, Element.HYDROGEN), AtomNode(4, Element.HYDROGEN),
            ),
            bonds = listOf(Bond(0, 1, 1), Bond(0, 2, 1), Bond(0, 3, 1), Bond(0, 4, 1)),
        )
        assertEquals(11.26f, methane.ionizationEnergy!!, 0.001f)
    }

    @Test
    fun photonAboveIpIonizesMoleculeIntoCationAndElectron() {
        val gen = CapturingGenerator()
        val rule = MolecularPhotoIonization(gen)
        val w = water()
        val ph = photon(energy = 15f)   // 15 > IP(13.6)

        assertTrue(rule.matchesMolecule(listOf(w, ph)))
        val outcome = rule.produce()

        // Молекула ВЫЖИВАЕТ: потребляется только фотон (в отличие от распада, где гибнет и молекула).
        assertEquals(listOf<Entity>(ph), outcome.consumed)

        // Заряд молекулы меняется через updateState: тот же граф, electrons 10 → 9 (катион +1).
        outcome.updateState.forEach { it() }
        assertEquals(9, w.state().value.electrons)
        assertTrue(w.state().value.species is Species.Molecular)   // граф не подменён

        // Вылетает ровно один электрон (нейтральный e⁻), энергия избытка ушла в скорость, не в energy.
        outcome.spawn.forEach { it() }
        assertEquals(1, gen.spawned.size)
        val e = gen.spawned.single()
        assertEquals(Element.ELECTRON, (e.species as Species.Elemental).element)
        assertEquals(1, e.electrons)
        assertEquals(0f, e.energy, 0.001f)
    }

    @Test
    fun photonBelowIpDoesNotIonize() {
        // Фотон 12 эВ < IP(13.6): ионизации нет (хотя это выше порога распада O–H = 4.8 — им займётся PhotoDissociation).
        val rule = MolecularPhotoIonization(CapturingGenerator())
        assertFalse(rule.matchesMolecule(listOf(water(), photon(energy = 12f))))
    }

    @Test
    fun noPhotonNoIonization() {
        // Рядом атом H, но фотона нет → ионизация не запускается.
        val rule = MolecularPhotoIonization(CapturingGenerator())
        val h = Atom(nextId++, Element.HYDROGEN, Position(1f, 0f), Vec2D(0f, 0f), 0f, 0f, electrons = 1)
            .also { it.setEnvironment(env) }
        assertFalse(rule.matchesMolecule(listOf(water(), h)))
    }

    @Test
    fun farPhotonDoesNotIonize() {
        // Фотон за пределами радиуса активации (MOLECULE_RADIUS = 20) не ионизует.
        val rule = MolecularPhotoIonization(CapturingGenerator())
        assertFalse(rule.matchesMolecule(listOf(water(), photon(energy = 20f, x = 1000f))))
    }
}