package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StateUpdate
import maratmingazovr.ai.carsonella.randomDirection

/**
 * Тепловая (ударная) ионизация в недрах звезды.
 *
 * В отличие от [PhotoIonization] (холодный атом ловит ОДИН фотон) — здесь причина не
 * отдельный фотон, а сама температура среды: при звёздных T частицы непрерывно
 * сталкиваются и срывают электроны, вещество превращается в плазму (голые ядра +
 * свободные электроны). Степень ионизации в реальности описывается уравнением Саха;
 * в горячих недрах ионизация полная.
 *
 * Модель упрощена: атом в среде с TemperatureMode.Star теряет по ОДНОМУ электрону за тик,
 * пока не останется голое ядро. Это приводит дропнутые в звезду нейтральные атомы к
 * «звёздному» голому виду, на котором работают правила синтеза.
 *
 * Триггерится одно-реагентным запросом listOf(this), который атом шлёт из Atom.step(),
 * когда находится в Star-температуре и ещё имеет электроны.
 */
class StarThermalIonization(
    private val entityGenerator: IEntityGenerator,
) : AtomReactionRule() {
    override val id = "StarThermalIonization"

    /** [element] выяснен в matchesAtom — produce не вычисляет заново. */
    private data class Match(val atom: Atom, val element: Element) : MatchedData

    override fun matchesAtom(atom: Atom, neighbors: List<Entity>): MatchedData? {
        if (neighbors.isNotEmpty()) return null
        val element = atom.element
        if (atom.electrons <= 0) return null
        if (atom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return null

        return Match(atom, element)
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (atom, element) = match as Match
        val electrons = atom.electrons
        val position = atom.state().value.kinematics.position
        val radius = element.details.radius
        val env = atom.getEnvironment()
        val electronPosition = position.plus(Position(radius, 0f))

        // Атом теряет электрон: тот же Element, на 1 электрон меньше; вылетает свободный e⁻. Водород
        // ничем не особен — H с одним электроном становится H с нулём, то есть протоном. energy сбрасываем в
        // основное состояние: у нового зарядового состояния другие уровни, старая энергия для него не
        // валидна (инвариант Atom; конструкторный require не ловит updateState-путь — чиним здесь).
        return ReactionOutcome(
            updateState = listOf(StateUpdate(atom) { atom.electrons = electrons - 1; atom.setEnergy(0f) }),
            spawn = listOf {
                entityGenerator.createEntity(
                    Element.ELECTRON,
                    electronPosition,
                    randomDirection(entityGenerator.random),
                    10f,
                    0f,
                    env,
                    electrons = 1
                )
            },
        )
    }
}