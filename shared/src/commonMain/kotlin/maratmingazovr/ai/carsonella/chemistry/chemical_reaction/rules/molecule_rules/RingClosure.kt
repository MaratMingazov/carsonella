package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.MoleculeRingCandidate
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.ReactionSelection
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ForcedReactionRule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StateUpdate
import maratmingazovr.ai.carsonella.chemistry.graph.BondEnergy
import maratmingazovr.ai.carsonella.randomDirection

/**
 * Замыкание кольца (Стадия 2): два ненасыщенных атома ОДНОЙ молекулы связываются → цикл (циклопентан,
 * бензол-скелет, дальше листы/каркасы). Брат [BondStrengthening]: внутримолекулярная реакция «сам с собой»
 * (`reagents.size == 1`), produce = мутация молекулы на месте + фотон.
 *
 * ТОЛЬКО ПО КЛИКУ игрока ([ForcedReactionRule]), как и усиление связи: сама собой цепь не сворачивается.
 * Спонтанная циклизация — ассоциация через активационный барьер (концы цепи должны сойтись под нужным
 * углом), а барьеров модель не знает; без них энергетика замыкала бы даже напряжённые кольца, едва цепь
 * дорастёт до трёх атомов (C–C 3.59 эВ против напряжения 1.17 у трёхчленного).
 *
 * Кандидатов даёт [Molecule.ringClosureCandidates] (пары со свободными слотами, путь ≥ 4 → кольцо ≥ 5).
 * КАКУЮ пару замкнуть, правило пока решает само — по `энергия новой связи − ringStrain` (байеровское
 * напряжение), то есть 6 выгоднее 5, а 5 выгоднее 7+. Когда появится клик по двум атомам, выбор приедет
 * параметром в [ReactionSelection.CloseRing] — как связь у усиления, и тогда же станет ненужным пол
 * размера кольца в графе (он стоит там ровно от спонтанного схлопывания).
 *
 * Геометрию НЕ моделируем: замыкание решается по длине пути в графе, а не по «сближению концов в
 * пространстве» (конформации/гибкость цепи — отдельный тяжёлый слой; см. docs/molecule-graph.md).
 */
class RingClosure(
    private val entityGenerator: IEntityGenerator,
) : ForcedReactionRule {
    override val id = "RingClosure"

    private data class Match(val molecule: Molecule, val candidate: MoleculeRingCandidate) : MatchedData

    override fun matches(reagents: List<Entity>, selection: ReactionSelection.Forced): MatchedData? {
        if (selection !is ReactionSelection.CloseRing) return null   // чужой выбор — не наш
        if (reagents.size != 1) return null   // форс приходит self-запросом (World.requestMoleculeAction)
        val subject = reagents.first() as? Molecule ?: return null
        if (!subject.state().value.alive) return null
        if (subject.getEnvironment().getEnvTemperature() == TemperatureMode.Star) return null   // в звезде молекул нет
        // Кандидат с максимальным выигрышем (энергия связи − напряжение): 5–6 бьют 7+.
        // null-выигрыш (энергия связи неизвестна) отсеиваем.
        val best = subject.ringClosureCandidates
            .mapNotNull { cand -> closureWeight(cand)?.let { cand to it } }
            .maxByOrNull { it.second }
            ?: return null
        return Match(subject, best.first)
    }

    /**
     * Молекула реакцию ПЕРЕЖИВАЕТ: атомов не прибавилось, добавилась связь — это та же сущность, поэтому
     * исход мутирует её через [StateUpdate], как и усиление связи ([BondStrengthening]). Заодно сохраняется
     * id, а с ним и выделение молекулы у игрока.
     */
    override fun produce(match: MatchedData): ReactionOutcome {
        val (molecule, cand) = match as Match
        val state = molecule.state().value
        val env = molecule.getEnvironment()

        // Нетто-энергия (энергия связи − напряжение кольца) уносится фотоном; напряжение остаётся запасённым
        // в геометрии кольца, которую мы явно не моделируем (потому фотон несёт нетто, а не полную энергию связи).
        val released = closureWeight(cand) ?: 0f

        val spawn = mutableListOf<() -> Entity>()
        if (released > 0f) {
            spawn += {
                // Фотон уносит нетто-энергию и УЛЕТАЕТ (скорость 40, как в BondStrengthening/SpontaneousEmission):
                // за тик покидает радиус активации, иначе PhotoDissociation мог бы поймать его и раскрыть кольцо.
                entityGenerator.createEntity(Element.PHOTON, state.kinematics.position, randomDirection(entityGenerator.random),
                    MAX_VELOCITY, energy = released, environment = env, electrons = 0)
            }
        }

        return ReactionOutcome(
            spawn = spawn,
            updateState = listOf(StateUpdate(molecule) { molecule.closeRing(cand.atom1, cand.atom2) }),
        )
    }

    // weight замыкания: энергия образуемой связи (BondEnergy, order=1) − напряжение кольца.
    // null, если энергия связи неизвестна (не CHNO) — тогда кандидат пропускается.
    // Связи ещё нет, поэтому её энергии нет и в кеше графа — идём в каталог (в отличие от MoleculeBond.energy).
    private fun closureWeight(cand: MoleculeRingCandidate): Float? {
        val bondE = BondEnergy.of(cand.atom1.structure.isotope, cand.atom2.structure.isotope, 1) ?: return null
        return bondE - ringStrain(cand.ringSize)
    }
}

// Байеровское напряжение кольца по числу атомов (эВ; порядок реальных значений: ккал/моль → эВ). 3–4 сильно
// напряжены, 5–6 почти/без напряжения, 7 чуть, 8+ мягко растёт (трансаннулярное/энтропия). 3–4 сюда не
// доходят — их отсекает пол RING_MIN_SIZE в ringClosureCandidates; оставлены для полноты/устойчивости.
private fun ringStrain(size: Int): Float = when (size) {
    3 -> 1.17f
    4 -> 1.13f
    5 -> 0.29f
    6 -> 0.0f
    7 -> 0.29f
    else -> 0.40f + 0.05f * (size - 8).coerceAtLeast(0)
}