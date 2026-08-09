package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

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


    private fun spawnFragments(
        fragments: List<MoleculeShape>,
        molecule: Molecule,
        generator: IEntityGenerator,
        energyPerFragment: Float,
    ): List<() -> Entity> {
        val env = molecule.getEnvironment()
        return fragments.map { frag ->
            val electrons = frag.atoms.sumOf { it.isotope.details.p }  // нейтральный осколок (гомолитика)
            if (frag.atoms.size == 1) {
                val atom = frag.atoms.single()
                val kinematics = atom.kinematics
                val kineticVelocity = kinematics.velocity + KINETIC_VELOCITY_PER_EV * energyPerFragment
                return@map { generator.createEntity(atom.isotope, kinematics.position, kinematics.direction, kineticVelocity, 0f, env, electrons) }
            } else {
                return@map { generator.createMolecule(frag, energyPerFragment, env, electrons) }
            }
        }
    }
}

// Перевод избытка энергии распада (эВ) в кинетику осколка-атома. Та же шкала, что у вылетающего электрона
// в PhotoIonization (0.2 * freeEnergy) — консистентный игровой коэффициент, не физическое v=√(2E/m).
private const val KINETIC_VELOCITY_PER_EV = 0.2f