package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Environment
import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.SubAtom
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.ChemicalReactionResolver
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.ReactionRequest
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.PhotoIonization
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Регрессия: исход, собранный по ОДНОМУ запросу, не должен портиться от `matches()` по СЛЕДУЮЩЕМУ.
 *
 * Экземпляр правила один на весь резолвер, а `resolve()` перебирает все запросы инициатора и по каждому
 * прогоняет `matches()` у ВСЕХ правил. Пока контекст реакции жил в полях правила, второй (пусть даже
 * неуспешный) `matches()` обнулял их — и отложенные лямбды уже выбранного исхода падали с NPE либо,
 * что хуже, молча меняли чужую частицу. Теперь контекст едет в [MatchedData], протухать нечему.
 *
 * Сценарий из жизни (лог игрока): в водород выстрелили фотоном 12.09 эВ, атом возбудился; затем
 * Luminescence сбросила его на 10.2 эВ, родив фотон 1.89 эВ рядом. На следующем тике `Atom.step()`
 * шлёт ДВА запроса с одним инициатором — [H, фотон] (есть сосед) и [H] (energy > 0). По первому
 * PhotoIonization резонансно поглощает фотон (10.2 + 1.89 ≈ уровень 12.09), по второму — не матчится.
 */
class StaleMatchRegressionTest {

    private class StubGenerator : IEntityGenerator {
        override val random = Random(0)
        override fun createEntity(
            species: Species, position: Position, direction: Vec2D,
            velocity: Float, energy: Float, environment: IEnvironment, electrons: Int,
        ): Entity = Atom(0L, Element.HYDROGEN, position, direction, velocity, energy = 0f, electrons = 1)
    }

    private val env = Environment()
    private var nextId = 1L

    private fun atom(element: Element, x: Float, electrons: Int, energy: Float): Atom =
        Atom(nextId++, element, Position(x, 0f), Vec2D(0f, 0f), 0f, energy, electrons)
            .also { it.setEnvironment(env) }

    // Фотон — SubAtom, а не Atom: у атома энергия обязана быть уровнем из таблицы, у фотона она произвольна.
    private fun photon(energy: Float, x: Float): SubAtom =
        SubAtom(nextId++, Element.PHOTON, Position(x, 0f), Vec2D(1f, 0f), velocity = 10f, energy = energy, electrons = 0)
            .also { it.setEnvironment(env) }

    @Test
    fun secondRequestDoesNotSpoilOutcomeOfTheFirst() {
        val resolver = ChemicalReactionResolver(StubGenerator())
        val hydrogen = atom(Element.HYDROGEN, 0f, electrons = 1, energy = 10.2f)
        val photon = photon(energy = 1.890003f, x = 1f)

        // Ровно то, что шлёт Atom.step(): сначала [H, фотон], следом [H] — оба от одного инициатора.
        val outcome = resolver.resolve(
            listOf(
                ReactionRequest(listOf(hydrogen, photon)),
                ReactionRequest(listOf(hydrogen)),
            )
        )

        // До фикса здесь падал NPE: лямбда исхода читала обнулённое поле правила.
        assertNotNull(outcome).updateState.forEach { it() }
        assertEquals(12.09f, hydrogen.state().value.energy)   // энергия «снапнута» на точный уровень
    }

    @Test
    fun failedMatchLeavesNoResidueForTheNextRequest() {
        // Зеркальный случай: сначала неуспешный матч, потом успешный. Правило не должно тащить состояние
        // между вызовами ни в одну сторону — matches() обязан быть чистой функцией от реагентов.
        val rule = PhotoIonization(StubGenerator())
        val hydrogen = atom(Element.HYDROGEN, 0f, electrons = 1, energy = 10.2f)
        val photon = photon(energy = 1.890003f, x = 1f)

        assertNull(rule.matches(listOf(hydrogen)))                       // одиночный реагент — мимо
        val match = assertNotNull(rule.matches(listOf(hydrogen, photon)))
        assertNull(rule.matches(listOf(hydrogen)))                       // ещё один промах после успеха

        // Матч, снятый ДО двух промахов, всё ещё полностью рабочий.
        rule.produce(match).updateState.forEach { it() }
        assertEquals(12.09f, hydrogen.state().value.energy)
    }
}
