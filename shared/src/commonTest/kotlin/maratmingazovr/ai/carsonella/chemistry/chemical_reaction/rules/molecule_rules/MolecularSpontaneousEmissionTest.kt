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
import maratmingazovr.ai.carsonella.chemistry.graph.BondEnergy
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Спонтанный сброс внутренней энергии молекулы: предиссоциация (E ≥ порог слабейшей связи → распад БЕЗ
 * фотона) ИЛИ излучение (иначе → весь избыток одним фотоном, energy → 0). Гейты: energy > 0, не Star.
 */
class MolecularSpontaneousEmissionTest {

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

    // H₂O: O(0)–H(1), O(0)–H(2). Слабейшая связь — O–H (4.80 эВ). energy — внутренняя энергия молекулы.
    private fun water(energy: Float, environment: IEnvironment = env): Molecule {
        val graph = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.OXYGEN_16), AtomNode(1, Element.HYDROGEN), AtomNode(2, Element.HYDROGEN)),
            bonds = listOf(Bond(0, 1, order = 1), Bond(0, 2, order = 1)),
        )
        return Molecule(nextId++, graph, Position(0f, 0f), Vec2D(0f, 0f), 0f, energy, electrons = 10)
            .also { it.setEnvironment(environment) }
    }

    // H₂O₂ (перекись): H(0)–O(1)–O(2)–H(3). Слабейшая связь — O–O (1.51 эВ).
    private fun peroxide(energy: Float): Molecule {
        val graph = MoleculeGraph(
            nodes = listOf(
                AtomNode(0, Element.HYDROGEN), AtomNode(1, Element.OXYGEN_16),
                AtomNode(2, Element.OXYGEN_16), AtomNode(3, Element.HYDROGEN),
            ),
            bonds = listOf(Bond(0, 1, order = 1), Bond(1, 2, order = 1), Bond(2, 3, order = 1)),
        )
        return Molecule(nextId++, graph, Position(0f, 0f), Vec2D(0f, 0f), 0f, energy, electrons = 18)
            .also { it.setEnvironment(env) }
    }

    @Test
    fun predissociatesWhenEnergyExceedsWeakestBond() {
        // Внутренняя энергия перекиси выше порога O–O (1.51) → распад САМА, без фотона.
        val gen = CapturingGenerator()
        val rule = MolecularSpontaneousEmission(gen)
        val ooBond = BondEnergy.of(Element.OXYGEN_16, Element.OXYGEN_16, 1)!!   // 1.51
        val mol = peroxide(energy = ooBond + 1f)

        assertTrue(rule.matchesMolecule(listOf(mol)))
        val outcome = rule.produce()

        assertEquals(listOf<Entity>(mol), outcome.consumed)              // потребляется только молекула — фотона нет
        outcome.spawn.forEach { it() }
        assertEquals(2, gen.spawned.size)                               // два осколка ·OH
        assertTrue(gen.spawned.all { it.species is Species.Molecular })
        gen.spawned.forEach { assertEquals("HO", (it.species as Species.Molecular).graph.formula) }
        assertEquals(18, gen.spawned.sumOf { it.electrons })            // электроны сохранены (9 + 9)
        // Избыток (E − порог) разложен по осколкам-молекулам (оба — молекулы → во внутреннюю энергию).
        assertEquals((ooBond + 1f) - ooBond, gen.spawned.sumOf { it.energy.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun radiatesAllEnergyAsPhotonBelowThreshold() {
        // 0 < E (1.0) < порог O–H (4.80) → ветка излучения: весь избыток одним фотоном, молекула → energy 0.
        val gen = CapturingGenerator()
        val rule = MolecularSpontaneousEmission(gen)
        val mol = water(energy = 1f)

        // Эмиссия стохастична (chance): крутим matches, пока не сработает (сид фиксирован → детерминизм).
        var fired = false
        for (i in 0 until 10_000) { if (rule.matchesMolecule(listOf(mol))) { fired = true; break } }
        assertTrue(fired, "ветка излучения так и не сработала за 10k попыток")

        val outcome = rule.produce()
        assertTrue(outcome.consumed.isEmpty())                          // ничего не потребляется
        outcome.updateState.forEach { it() }
        assertEquals(0f, mol.state().value.energy, 0.001f)              // молекула сброшена в основное состояние

        outcome.spawn.forEach { it() }
        assertEquals(1, gen.spawned.size)
        val photon = gen.spawned.single()
        assertEquals(Element.PHOTON, (photon.species as Species.Elemental).element)
        assertEquals(1f, photon.energy, 0.001f)                        // фотон унёс всю внутреннюю энергию
    }

    @Test
    fun doesNotFireWithoutEnergy() {
        val rule = MolecularSpontaneousEmission(CapturingGenerator())
        assertFalse(rule.matchesMolecule(listOf(water(energy = 0f))))   // остывать нечего
    }

    @Test
    fun doesNotFireInStar() {
        // В звезде распадом рулит StarDissociation — это правило не вмешивается, даже с большой энергией.
        val star = Environment(temperature = TemperatureMode.Star)
        val rule = MolecularSpontaneousEmission(CapturingGenerator())
        assertFalse(rule.matchesMolecule(listOf(water(energy = 100f, environment = star))))
    }
}