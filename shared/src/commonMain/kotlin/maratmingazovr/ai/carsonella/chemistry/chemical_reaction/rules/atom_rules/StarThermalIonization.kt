package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.ElementType
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
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

    private var entity: Entity? = null
    private var subjectElement: Element? = null   // запомнен в matchesAtoms — produce не вычисляет заново

    override fun matchesAtoms(reagents: List<Entity>): Boolean {
        entity = null
        subjectElement = null

        if (reagents.size != 1) return false
        val first = reagents.first()
        if (!first.state().value.alive) return false
        // species в локальный val → smart-cast к Elemental ниже (через Entity компилятор сам этого не знает).
        val species = first.state().value.species
        if (species !is Species.Elemental) return false
        val element = species.element
        if (element.details.type != ElementType.Atom) return false
        if (first.state().value.electrons <= 0) return false
        if (first.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false

        entity = first
        subjectElement = element
        return true
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {
        val atom = entity!!
        val element = subjectElement!!   // запомнили в matchesAtoms
        val electrons = atom.state().value.electrons
        val position = atom.state().value.position
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