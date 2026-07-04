package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Environment
import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.CovalentBondFormation
import maratmingazovr.ai.carsonella.chemistry.graph.BondEnergy
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Шаг 3a: правило ковалентной связи. Проверяем образование H₂ из двух нейтральных H end-to-end
 * (matches → produce → spawn → Molecular-граф) и отказы (благородный газ, ион, далеко друг от друга).
 */
class CovalentBondFormationTest {

    // Фейковый генератор: копит все спавны (species + energy) — теперь их два: молекула + фотон связи.
    private class CapturingGenerator : IEntityGenerator {
        override val random = Random(0)
        data class Spawned(val species: Species, val energy: Float)
        val spawned = mutableListOf<Spawned>()
        override fun createEntity(
            species: Species, position: Position, direction: Vec2D,
            velocity: Float, energy: Float, environment: IEnvironment, electrons: Int,
        ): Entity<*> {
            spawned += Spawned(species, energy)
            return Atom(0L, Element.HYDROGEN, position, direction, velocity, energy, electrons = 1)
        }
    }

    private val env = Environment()   // дефолт: TemperatureMode.Space
    private var nextId = 1L

    private fun atom(element: Element, x: Float, electrons: Int): Atom =
        Atom(nextId++, element, Position(x, 0f), Vec2D(0f, 0f), 0f, 0f, electrons)
            .also { it.setEnvironment(env) }

    @Test
    fun twoNeutralHydrogensFormH2() {
        val gen = CapturingGenerator()
        val rule = CovalentBondFormation(gen)
        val h1 = atom(Element.HYDROGEN, 0f, electrons = 1)
        val h2 = atom(Element.HYDROGEN, 3f, electrons = 1)

        assertTrue(rule.matches(listOf(h1, h2)))

        val outcome = rule.produce()
        assertEquals(listOf<Entity<*>>(h1, h2), outcome.consumed)   // оба реагента поглощаются

        outcome.spawn.forEach { it() }                              // выполнить спавны → молекула + фотон
        val molecule = gen.spawned.map { it.species }.filterIsInstance<Species.Molecular>().single()
        assertEquals("H2", molecule.graph.formula())
        assertEquals(2f, molecule.graph.mass())
        // Образование связи экзотермично → фотон с энергией связи H–H (радиационная ассоциация).
        val photon = gen.spawned.single { (it.species as? Species.Elemental)?.element == Element.PHOTON }
        assertEquals(BondEnergy.of(Element.HYDROGEN, Element.HYDROGEN, 1), photon.energy)
    }

    @Test
    fun nobleGasDoesNotBond() {
        val rule = CovalentBondFormation(CapturingGenerator())
        val he1 = atom(Element.HELIUM_4, 0f, electrons = 2)
        val he2 = atom(Element.HELIUM_4, 3f, electrons = 2)
        assertFalse(rule.matches(listOf(he1, he2)))   // валентность He = 0
    }

    @Test
    fun ionizedAtomDoesNotBond() {
        val rule = CovalentBondFormation(CapturingGenerator())
        val neutral = atom(Element.HYDROGEN, 0f, electrons = 1)
        val ion = atom(Element.HYDROGEN, 3f, electrons = 0)   // не нейтральный — нет электронов для пары
        assertFalse(rule.matches(listOf(neutral, ion)))
    }

    @Test
    fun farApartDoNotBond() {
        val rule = CovalentBondFormation(CapturingGenerator())
        val h1 = atom(Element.HYDROGEN, 0f, electrons = 1)
        val h2 = atom(Element.HYDROGEN, 1000f, electrons = 1)
        assertFalse(rule.matches(listOf(h1, h2)))
    }
}