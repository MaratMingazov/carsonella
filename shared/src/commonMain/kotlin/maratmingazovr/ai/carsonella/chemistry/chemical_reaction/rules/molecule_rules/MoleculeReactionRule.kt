package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.MoleculeBond
import maratmingazovr.ai.carsonella.chemistry.MoleculeShape
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StateUpdate

/**
 * Базовый класс для молекулярных правил (рост 3b, граф-диссоциация, …): субъект реакции — первый
 * реагент — ОБЯЗАН быть [Molecule]. База разбирает запрос на субъект и соседей, поэтому наследник
 * получает уже типизированную молекулу и хвост отдельным аргументом.
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
        val molecule = reagents.firstOrNull() as? Molecule ?: return null
        if (!molecule.alive) return null
        return matchesMolecule(molecule, reagents.subList(1, reagents.size))
    }

    /**
     * Как `matches`, но разложенное на части: субъект приходит типизированным (кастовать не нужно),
     * а [neighbors] — ХВОСТ запроса, то есть соседи, которых нашёл инициатор, БЕЗ него самого.
     * Пустой хвост = self-запрос («сам с собой»: остывание, распад в звезде).
     *
     * [neighbors] — вью на исходный список (`subList`), а не копия: `matches` гоняется по всем правилам
     * на каждый запрос, и копировать хвост на каждое из них незачем. Вью живёт ровно столько же, сколько
     * сам запрос, и никто его не мутирует.
     */
    abstract fun matchesMolecule(molecule: Molecule, neighbors: List<Entity>): MatchedData?

    /**
     * Разрыв связи [bond] — ОБЩИЙ исход для всех, кто рвёт связи (фотодиссоциация, распад в звезде,
     * предиссоциация). [energyToShare] — энергия, остающаяся продуктам после того, как порог оплатил
     * разрыв; сколько её, решает вызывающий (у него свой источник: фотон, тепло звезды, своя внутренняя).
     *
     * Случая ДВА, и различает их сама связь:
     *  - КОЛЬЦЕВАЯ ([MoleculeBond.inRing]) — граф остаётся связным: молекула не распадается, кольцо
     *    раскрывается в цепь. Это 1→1, поэтому мутация на месте ([Molecule.openRing]), id и выделение
     *    игрока живут, а энергия остаётся внутренней энергией той же молекулы.
     *  - МОСТ — молекула гибнет, осколки рождаются заново (см. [spawnFragments]).
     *
     * Правилу эту развилку знать не нужно, поэтому она здесь, а не в трёх produce.
     */
    protected fun breakBond(
        molecule: Molecule,
        bond: MoleculeBond,
        generator: IEntityGenerator,
        energyToShare: Float,
    ): ReactionOutcome {
        val energy = energyToShare.coerceAtLeast(0f)
        if (bond.inRing) {
            return ReactionOutcome(
                updateState = listOf(StateUpdate(molecule) {
                    molecule.openRing(bond)
                    molecule.energy = energy
                }),
            )
        }
        val fragments = molecule.split(bond)
        return ReactionOutcome(
            consumed = listOf(molecule),
            spawn = spawnFragments(fragments, molecule, generator, energy / fragments.size),
        )
    }

    /**
     * Спавн осколков распада ([Molecule.split]) — общий для PhotoDissociation/StarDissociation
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
    private fun spawnFragments(
        fragments: List<MoleculeShape>,
        molecule: Entity,
        generator: IEntityGenerator,
        energyPerFragment: Float,
    ): List<() -> Entity> {
        val kinematics = molecule.kinematics
        val env = molecule.getEnvironment()
        return fragments.mapIndexed { i, frag ->
            // Разводим осколки по оси X. Шаг между соседями обязан ПРЕВЫШАТЬ дистанцию повторной связи
            // CovalentBondFormation (√2·r ≈ 28 при r = 20), иначе атомы-осколки тут же связываются обратно.
            // Дальше их держит порознь взаимное отталкивание (оба нейтральны, см. calculateForce).
            // Настоящие координаты у осколка уже есть (форма их несёт), но взять их вместо этой развозки
            // нельзя: осколки встали бы вплотную и слиплись бы обратно тем же тиком — см. док, шаг 4.
            val pos = kinematics.position.plus(Position((i - (fragments.size - 1) / 2f) * molecule.radius * FRAGMENT_SEPARATION, 0f))
            val electrons = frag.atoms.sumOf { it.structure.isotope.details.p }  // нейтральный осколок (гомолитика)
            if (frag.atoms.size == 1) {
                val isotope = frag.atoms.single().structure.isotope
                val kineticVelocity = kinematics.velocity + KINETIC_VELOCITY_PER_EV * energyPerFragment
                return@mapIndexed { generator.createEntity(isotope, pos, kinematics.direction, kineticVelocity, 0f, env, electrons) }
            } else {
                return@mapIndexed { generator.createMolecule(frag, pos, kinematics.direction, kinematics.velocity, energyPerFragment, env, electrons) }
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