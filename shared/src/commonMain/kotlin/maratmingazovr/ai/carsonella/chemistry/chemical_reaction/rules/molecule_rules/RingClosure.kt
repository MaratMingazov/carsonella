package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.graph.BondEnergy
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import maratmingazovr.ai.carsonella.chemistry.graph.RingClosureCandidate
import maratmingazovr.ai.carsonella.randomDirection

/**
 * Замыкание кольца (Стадия 2): два ненасыщенных атома ОДНОЙ молекулы связываются → цикл (циклопентан,
 * бензол-скелет, дальше листы/каркасы). Брат [BondStrengthening]: внутримолекулярная реакция «сам с собой»
 * (`reagents.size == 1`), топология + энергетический `weight`, produce = новый граф + фотон.
 *
 * Кандидатов даёт [MoleculeGraph.ringClosureCandidates] (пары со свободными слотами, путь ≥ 4 → кольцо ≥ 5;
 * напряжённые 3–4 отсечены полом — см. там). Среди кандидатов выбираем по `weight = энергия новой связи −
 * ringStrain` (байеровское напряжение): 6-кольцо (strain ≈ 0) конкурирует с ростом цепи, 5 чуть слабее,
 * 7+ ещё слабее — так углерод перестаёт расти бесконечно и начинает сворачиваться (эмёрджентно).
 *
 * Геометрию НЕ моделируем: замыкание решается по длине пути в графе + `weight`, а не по «сближению концов в
 * пространстве» (конформации/гибкость цепи — отдельный тяжёлый слой; см. docs/molecule-graph.md).
 */
class RingClosure(
    private val entityGenerator: IEntityGenerator,
) : MoleculeReactionRule() {
    override val id = "RingClosure"

    private var molecule: Entity<*>? = null
    private var candidate: RingClosureCandidate? = null

    override fun matchesMolecule(reagents: List<Entity<*>>): Boolean {
        molecule = null
        candidate = null
        if (reagents.size != 1) return false   // как усиление/распады: только «сам с собой», без соседей
        val first = reagents.first()
        if (!first.state().value.alive) return false
        if (first.getEnvironment().getEnvTemperature() == TemperatureMode.Star) return false   // в звезде молекул нет
        val graph = (first.state().value.species as Species.Molecular).graph
        // Выбираем кандидата с максимальным weight (энергия связи − напряжение), чтобы правило вышло в
        // resolve() своим сильнейшим вариантом (5–6 бьют 7+). null-вес (энергия связи неизвестна) отсеиваем.
        val best = graph.ringClosureCandidates
            .mapNotNull { cand -> closureWeight(graph, cand)?.let { cand to it } }
            .maxByOrNull { it.second }
            ?: return false
        molecule = first
        candidate = best.first
        return true
    }

    // Экзотермично: образование связи высвобождает энергию, но напряжение кольца её съедает.
    // weight = E(связь) − ringStrain(размер). Сравнивается с ростом/усилением в одном resolve(). Поля из matches.
    override fun weight(): Float {
        val mol = molecule ?: return 0f
        val cand = candidate ?: return 0f
        val graph = (mol.state().value.species as Species.Molecular).graph
        return closureWeight(graph, cand) ?: 0f
    }

    override fun produce(): ReactionOutcome {
        val mol = molecule!!
        val cand = candidate!!
        val state = mol.state().value
        val graph = (state.species as Species.Molecular).graph
        val closed = graph.closeRing(cand.atom1, cand.atom2)
        val env = mol.getEnvironment()

        // Нетто-энергия (энергия связи − напряжение кольца) уносится фотоном; напряжение остаётся запасённым
        // в геометрии кольца, которую мы явно не моделируем (потому фотон несёт нетто, а не полную энергию связи).
        val released = closureWeight(graph, cand) ?: 0f

        val spawn = mutableListOf<() -> Entity<*>>(
            { entityGenerator.createEntity(Species.Molecular(closed), state.position, state.direction, state.velocity, state.energy, env, state.electrons) },
        )
        if (released > 0f) {
            spawn += {
                // Фотон уносит нетто-энергию и УЛЕТАЕТ (скорость 40, как в BondStrengthening/SpontaneousEmission):
                // за тик покидает радиус активации, иначе PhotoDissociation мог бы поймать его и раскрыть кольцо.
                entityGenerator.createEntity(Element.PHOTON, state.position, randomDirection(entityGenerator.random), 40f, energy = released, environment = env, electrons = 0)
            }
        }

        return ReactionOutcome(
            consumed = listOf(mol),
            spawn = spawn,
            description = "$id: ${graph.formulaPretty} замыкание ${cand.atom1}-${cand.atom2} → кольцо ${cand.ringSize}" +
                (if (released > 0f) " + γ[${released}eV]" else ""),
        )
    }

    // weight замыкания: энергия образуемой связи (BondEnergy, order=1) − напряжение кольца.
    // null, если энергия связи неизвестна (не CHNO) — тогда кандидат пропускается.
    private fun closureWeight(graph: MoleculeGraph, cand: RingClosureCandidate): Float? {
        val isoA = graph.nodes.first { it.localId == cand.atom1 }.isotope
        val isoB = graph.nodes.first { it.localId == cand.atom2 }.isotope
        val bondE = BondEnergy.of(isoA, isoB, 1) ?: return null
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