package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph

/**
 * Базовый класс для молекулярных правил (рост 3b, граф-диссоциация, …): субъект реакции
 * (`reagents.first()`) ОБЯЗАН быть [Molecule] — и приходит наследнику уже типизированным.
 *
 * Симметрично AtomReactionRule, но с двумя отличиями:
 *  - гейтит субъект на [Molecule] (а не на «не молекулу»);
 *  - хвост НЕ фильтрует — партнёром молекулы законно бывает и атом (рост атом+молекула, фотон при
 *    диссоциации), и другая молекула (рост молекула+молекула). Правила-наследники читают граф/`species`,
 *    а НЕ шов [Entity.state]`.element`, поэтому соседи-молекулы их не роняют (в отличие от атомных правил,
 *    которым фильтр нужен именно ради безопасности `.element`).
 *
 * Так рост молекулы привязан к субъекту-молекуле: атом+атом собирает атомное правило
 * (`CovalentBondFormation`), а атом+молекула / молекула+молекула — правило отсюда.
 */
abstract class MoleculeReactionRule : ReactionRule {

    final override fun matches(reagents: List<Entity>): MatchedData? {
        val subject = reagents.firstOrNull() as? Molecule ?: return null
        return matchesMolecule(subject, reagents)
    }

    /** Как `matches`, но субъект приходит уже типизированным — кастовать в наследниках не нужно. */
    abstract fun matchesMolecule(subject: Molecule, reagents: List<Entity>): MatchedData?

    /**
     * Спавн осколков распада ([MoleculeGraph.split]) — общий для PhotoDissociation/StarDissociation
     * (одна графовая хирургия → один способ «выложить» осколки). Осколки разводятся по оси X от [molecule],
     * наследуют её направление, нейтральны (гомолитика: electrons = протоны осколка).
     *
     * КЛЮЧЕВОЕ — куда кладём [energyPerFragment] (долю энергии на осколок) зависит от типа осколка:
     *  - Молекула — во ВНУТРЕННЮЮ (колебательную) энергию: осколок «горячее» и легче
     *    распадётся дальше (каскад). У молекулы энергия квазинепрерывна — произвольное значение допустимо.
     *  - Атом — в КИНЕТИКУ (velocity), а energy = 0. Внутренняя энергия атома
     *    КВАНТОВАНА (только дискретные уровни, инвариант проверяет SpontaneousEmission), и избыток распада
     *    (обычно << первого уровня возбуждения) в неё не влезает. Положили бы в energy — атом получил бы
     *    «не-уровень» и уронил бы ассерт SpontaneousEmission на следующем тике. Резонансное электронное
     *    возбуждение осколка-атома (редкость) не моделируем — весь избыток идёт в движение.
     */
    protected fun spawnFragments(
        fragments: List<MoleculeGraph>,
        molecule: Entity,
        generator: IEntityGenerator,
        energyPerFragment: Float,
    ): List<() -> Entity> {
        val moleculeState = molecule.state().value
        val env = molecule.getEnvironment()
        return fragments.mapIndexed { i, frag ->
            // Разводим осколки по оси X. Шаг между соседями обязан ПРЕВЫШАТЬ дистанцию повторной связи
            // CovalentBondFormation (√2·r ≈ 28 при r = 20), иначе атомы-осколки тут же связываются обратно.
            // Дальше их держит порознь взаимное отталкивание (оба нейтральны, см. calculateForce).
            val pos = moleculeState.centerPosition.plus(Position((i - (fragments.size - 1) / 2f) * molecule.radius * FRAGMENT_SEPARATION, 0f))
            val electrons = frag.protons               // нейтральный осколок (гомолитика)
            if (frag.nodes.size == 1) {
                val isotope = frag.nodes.single().isotope
                val kineticVelocity = moleculeState.velocity + KINETIC_VELOCITY_PER_EV * energyPerFragment
                return@mapIndexed { generator.createEntity(isotope, pos, moleculeState.direction, kineticVelocity, 0f, env, electrons) }
            } else {
                return@mapIndexed { generator.createMolecule(frag, pos, moleculeState.direction, moleculeState.velocity, energyPerFragment, env, electrons) }
            }
        }
    }
}

// Перевод избытка энергии распада (эВ) в кинетику осколка-атома. Та же шкала, что у вылетающего электрона
// в PhotoIonization (0.2 * freeEnergy) — консистентный игровой коэффициент, не физическое v=√(2E/m).
private const val KINETIC_VELOCITY_PER_EV = 0.2f

// Множитель разведения осколков: шаг между соседями = radius * этот множитель. Обязан давать шаг больше
// дистанции повторной связи CovalentBondFormation (√2·r): при r = 20 порог ≈ 28, а 2.5·20 = 50 — с запасом.
private const val FRAGMENT_SEPARATION = 2.5f