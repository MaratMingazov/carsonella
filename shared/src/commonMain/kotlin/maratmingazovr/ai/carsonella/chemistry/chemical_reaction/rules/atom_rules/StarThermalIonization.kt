package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
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
 * Водород — особый случай: голый H это частица Proton (SubAtom), а не «H с 0 электронов»
 * (тот же приём consume + spawn, что и в [PhotoIonization]).
 *
 * Триггерится одно-реагентным запросом listOf(this), который атом шлёт из Atom.step(),
 * когда находится в Star-температуре и ещё имеет электроны.
 */
class StarThermalIonization(
    private val entityGenerator: IEntityGenerator,
) : AtomReactionRule() {
    override val id = "StarThermalIonization"

    /** [element] выяснен в matchesAtoms — produce не вычисляет заново. */
    private data class Match(val atom: Entity, val element: Element) : MatchedData

    override fun matchesAtoms(reagents: List<Entity>): MatchedData? {
        if (reagents.size != 1) return null
        val first = reagents.first() as? Atom ?: return null
        if (!first.state().value.alive) return null
        val element = first.element
        if (first.state().value.electrons <= 0) return null
        if (first.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return null

        return Match(first, element)
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (atom, element) = match as Match
        val electrons = atom.state().value.electrons
        val position = atom.state().value.centerPosition
        val radius = element.details.radius
        val env = atom.getEnvironment()
        val electronPosition = position.plus(Position(radius, 0f))

        // Водород: после срыва единственного электрона остаётся голый протон (частица Proton).
        if (element == Element.HYDROGEN) {
            return ReactionOutcome(
                consumed = listOf(atom),
                spawn = listOf {
                    entityGenerator.createEntity(
                        Element.Proton,
                        position,
                        atom.state().value.direction,
                        atom.state().value.velocity,
                        0f,
                        env,
                        electrons = 0
                    )
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
                description = "$id: ${element.label(electrons)} -> ${Element.Proton.details.label} + ${Element.ELECTRON.details.label}",
            )
        }

        // Прочие атомы: тот же Element, на 1 электрон меньше; вылетает свободный e⁻. energy сбрасываем в
        // основное состояние: у нового зарядового состояния другие уровни, старая энергия для него не
        // валидна (инвариант Atom; конструкторный require не ловит updateState-путь — чиним здесь).
        return ReactionOutcome(
            updateState = listOf { atom.setElectrons(electrons - 1); atom.setEnergy(0f) },
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
            description = "$id: ${element.label(electrons)} -> ${element.label(electrons - 1)} + ${Element.ELECTRON.details.label}",
        )
    }
}