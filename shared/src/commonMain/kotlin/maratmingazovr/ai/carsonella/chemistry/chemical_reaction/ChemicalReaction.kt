package maratmingazovr.ai.carsonella.chemistry.chemical_reaction

import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.AlphaDecay
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.AlphaProtonReaction
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.Annihilation
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.BetaMinusDecay
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.BetaPlusDecay
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.PhotoIonization
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ForcedReactionRule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.StarAlphaGammaReaction
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.StarAlphaNeutronReaction
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.StarNeutronGammaReaction
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.StarNeutronAlphaReaction
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.StarNeutronProtonReaction
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.StarProtonCaptureReaction
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.StarEmission
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.StarCarbonBurning
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.StarOxygenBurning
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.StarPPChain
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.StarPhotodisintegration
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.StarThermalIonization
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.RecombinationReaction
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.CovalentBondFormation
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.SpontaneousEmission
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules.PhotoDissociation
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules.MolecularPhotoIonization
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules.MoleculeGrowth
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules.BondStrengthening
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules.RingClosure
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules.StarDissociation
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules.MolecularSpontaneousEmission
import kotlin.random.Random


interface IEntityGenerator {
    val random: Random

    /** Атом, частица или звезда — по элементу. Какой класс строить, решает генератор. */
    fun createEntity(element: Element, position: Position, direction: Vec2D, velocity: Float, energy: Float, environment: IEnvironment, electrons: Int): Entity

    /** Молекула — по графу. Отдельный метод, а не перегрузка: у молекулы и элемента общего типа нет. */
    fun createMolecule(graph: MoleculeGraph, position: Position, direction: Vec2D, velocity: Float, energy: Float, environment: IEnvironment, electrons: Int): Entity
    fun createMolecule(atom1: Atom, atom2: Atom, environment: IEnvironment,): Entity
}



class ChemicalReactionResolver(private val entityGenerator: IEntityGenerator) {

    private val rules = listOf(

        PhotoIonization(entityGenerator), // отрыв электрона от элемента под действием света
        PhotoDissociation(entityGenerator), // деление молекулы на атомы под действием света
        SpontaneousEmission(entityGenerator), // элемент в возбужденном состоянии может излучить фотон
        BetaPlusDecay(entityGenerator), // β⁺-распад протон-избыточных ядер (¹³N → ¹³C + e⁺, ¹⁵O → ¹⁵N + e⁺)
        BetaMinusDecay(entityGenerator), // β⁻-распад нейтрон-избыточных ядер (³¹Si → ³¹P + e⁻) — толкает s-процесс вверх по Z
        AlphaDecay(entityGenerator), // α-распад: ²¹⁰Po → ²⁰⁶Pb + ⁴He — замыкает свинцово-висмутовый цикл s-процесса

        StarEmission(entityGenerator),
        RecombinationReaction(entityGenerator),
        Annihilation(entityGenerator), // e⁻ + e⁺ → 2γ — без неё позитроны от β⁺-распада копились бы вечно
        StarAlphaGammaReaction(entityGenerator), // в недрах звезд элементы могут захватывать альфа частицы (ядра гелия) для образования более тяжелых элементов
        StarAlphaNeutronReaction(entityGenerator), // (α,n) в звезде: ¹⁸O→²¹Ne, ²²Ne→²⁵Mg, ²⁵Mg→²⁸Si. Главный нейтронный источник для s-процесса
        StarNeutronGammaReaction(entityGenerator), // (n,γ) в звезде: основа s-процесса. Захват нейтрона ядром, без кулоновского барьера. Цикл воспроизводства нейтронов через ¹²C(n,γ)¹³C(α,n)¹⁶O
        StarNeutronProtonReaction(entityGenerator), // (n,p) в звезде: A + n → A′ + p (Z→Z-1). ¹⁴N(n,p)¹⁴C — космогенный радиоуглерод; с β⁻ замыкает петлю ¹⁴N(n,p)¹⁴C(β⁻)¹⁴N
        StarNeutronAlphaReaction(entityGenerator), // (n,α) в звезде: A + n → A′ + ⁴He (Z→Z-2). ¹⁷O(n,α)¹⁴C — кормит ту же радиоуглеродную петлю
        StarProtonCaptureReaction(entityGenerator), // Объединённое (p,γ)/(p,α) в звезде. Покрывает CNO, NeNa, MgAl. Branching и rate захардкожены по target-ядру внутри правила; target+продукт берутся из Details. Roulette-wheel — один roll выбирает канал
        AlphaProtonReaction(entityGenerator), // (α,p) в космосе: A + ⁴He → A′ + p. Историческая ¹⁴N+α→¹⁷O+p (Резерфорд, 1919)
        StarPPChain(entityGenerator), // pp-цепочка: p+p→D⁺, D⁺+p→³He²⁺, ³He²⁺+³He²⁺→⁴He²⁺+2p, плюс pp-II финал ⁷Be+e⁻→⁷Li, ⁷Li+p→2⁴He
        StarCarbonBurning(entityGenerator), // горение углерода: ¹²C+¹²C → ²⁰Ne+⁴He / ²³Na+p / ²⁴Mg
        StarOxygenBurning(entityGenerator), // горение кислорода: ¹⁶O+¹⁶O → ²⁸Si+⁴He / ³¹P+p / ³¹S+n
        StarPhotodisintegration(entityGenerator), // (γ,X) в звезде: развал ядра жёстким γ — обратное к (α,γ)/(p,γ)/(n,γ). Сердце горения кремния: ²⁸Si(γ,α)²⁴Mg высвобождает α для α-цепочки к Fe
        StarThermalIonization(entityGenerator), // тепловая ионизация в недрах звезды: атом теряет по электрону за тик до голого ядра (плазма)

        // --- образование молекул (граф) ---
        CovalentBondFormation(entityGenerator), // ковалентная связь: два нейтральных лёгких атома → двухатомная молекула-граф
        MoleculeGrowth(entityGenerator), // рост молекулы (3b): молекула со свободным слотом + атом/молекула → бо́льшая молекула (O–H + H → H₂O)
        StarDissociation(entityGenerator), // распад в звезде: молекула в Star-среде рвёт слабейшую связь за тик, рекурсивно до атомов
        MolecularPhotoIonization(entityGenerator), // отрыв электрона от молекулы под действием света (E ≥ IP): молекула → катион + e⁻
        MolecularSpontaneousEmission(entityGenerator), // спонтанный сброс внутренней энергии: предиссоциация (E ≥ порог связи) ИЛИ излучение фотона (иначе)

    )

    /**
     * Правила, которые НИКОГДА не срабатывают сами — только по клику игрока (механика «лего», см.
     * [ForcedReactionRule]). Отдельный список, потому что у них другой контракт: на вход идёт ВЫБОР
     * игрока, а weight не нужен вовсе — конкуренции нет. Так эмёрджентный отбор физически не может
     * задеть правило, которое обязано ждать клика.
     */
    private val forcedRules = listOf<ForcedReactionRule>(
        BondStrengthening(entityGenerator), // усиление связи: игрок кликает по связи (O–O → O=O, N–N → N≡N)
        RingClosure(entityGenerator), // замыкание кольца: два ненасыщенных атома одной молекулы → цикл
    )

    /**
     * Разрешение реакций ОДНОГО инициатора: на вход — все его запросы за тик (первый реагент каждого —
     * сам инициатор). За тик объект делает ≤1 реакцию (после первой он `destroy()`), поэтому среди всех
     * совпавших выбираем ОДИН исход.
     *
     * ДВА режима (см. [ReactionSelection]):
     *  - ФОРС игрока ([ReactionSelection.Forced]) имеет ПРИОРИТЕТ: ищем среди [forcedRules] то, которое
     *    обслуживает этот выбор; конкуренции по weight нет — применимо, значит выполняется. Так явный клик
     *    (усиление связи / замыкание кольца) не перебивается эмёрджентной химией. Первый применимый
     *    форс-запрос побеждает.
     *  - ЭМЁРДЖЕНТНО (`WeightBased`, дефолт): среди всех применимых правил по всем weight-запросам берём
     *    исход с максимальным `weight` (тай-брейк случайно). Так рост молекулы конкурирует с распадами
     *    в одном месте (docs/molecule-graph.md §6).
     *
     * produce() зовём ОДИН раз и только для ПОБЕДИТЕЛЯ, в самом конце. Так можно, потому что весь
     * контекст реакции лежит в [MatchedData] — иммутабельном снимке, который вернул matches(). Раньше
     * контекст жил в полях правила, и produce() приходилось звать сразу: следующий matches() (пусть даже
     * неуспешный — он обнуляет поля в начале) испортил бы уже собранный исход. Проигравшие исходы теперь
     * вообще не вычисляются.
     */
    fun resolve(requests: List<ReactionRequest>): ReactionOutcome? {
        // 1. Форс игрока: его выбор, приоритет над weight. Первый применимый выигрывает.
        // Какое правило обслуживает выбор, знает само правило (сужает тип selection до своего).
        for (request in requests) {
            val selection = request.selection as? ReactionSelection.Forced ?: continue
            for (rule in forcedRules) {
                val match = rule.matches(request.reagents, selection) ?: continue
                return rule.produce(match)
            }
        }
        // 2. Эмёрджентно: лучший по weight среди всех правил по всем WeightBased-запросам.
        var best: Candidate? = null
        for (req in requests) {
            if (req.selection != ReactionSelection.WeightBased) continue
            val candidates = rules.mapNotNull { rule ->
                rule.matches(req.reagents)?.let { match -> Candidate(rule, match, rule.weight(match)) }
            }
            if (candidates.isEmpty()) continue
            val maxWeight = candidates.maxOf { it.weight }
            // Отбираем правила с максимальным весом и выбираем случайное из них
            val chosen = candidates.filter { it.weight == maxWeight }.random(entityGenerator.random)
            if (best == null || chosen.weight > best.weight) best = chosen
        }
        return best?.let { it.rule.produce(it.match) }
    }

    /** Сматчившееся правило вместе со своим матчем и посчитанным весом — кандидат на применение. */
    private data class Candidate(val rule: ReactionRule, val match: MatchedData, val weight: Float)
}