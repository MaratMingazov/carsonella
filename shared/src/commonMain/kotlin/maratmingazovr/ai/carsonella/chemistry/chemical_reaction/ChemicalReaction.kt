package maratmingazovr.ai.carsonella.chemistry.chemical_reaction

import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.AlphaDecay
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.AlphaProtonReaction
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.Annihilation
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.BetaMinusDecay
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.BetaPlusDecay
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.PhotoIonization
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
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
    fun createEntity(species: Species, position: Position, direction: Vec2D, velocity: Float, energy: Float, environment: IEnvironment, electrons: Int): Entity

    // Удобная перегрузка для Elemental — вызовы по Element (атомы/частицы/звезда/модули) не трогаем.
    fun createEntity(element: Element, position: Position, direction: Vec2D, velocity: Float, energy: Float, environment: IEnvironment, electrons: Int): Entity =
        createEntity(Species.Elemental(element), position, direction, velocity, energy, environment, electrons)
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
        BondStrengthening(entityGenerator), // усиление связи (3c): ненасыщенная связь молекулы 1→2→3 (O–O → O=O, N₂ → N≡N)
        RingClosure(entityGenerator), // замыкание кольца: два ненасыщенных атома одной молекулы → цикл (кольцо ≥ 5, weight по ringStrain: 5–6 выгодны)
        StarDissociation(entityGenerator), // распад в звезде: молекула в Star-среде рвёт слабейшую связь за тик, рекурсивно до атомов
        MolecularPhotoIonization(entityGenerator), // отрыв электрона от молекулы под действием света (E ≥ IP): молекула → катион + e⁻
        MolecularSpontaneousEmission(entityGenerator), // спонтанный сброс внутренней энергии: предиссоциация (E ≥ порог связи) ИЛИ излучение фотона (иначе)

    )

    /**
     * Разрешение реакций ОДНОГО инициатора: на вход — все списки реагентов, которые он запросил за тик
     * (первый элемент каждого — сам инициатор). За тик объект делает ≤1 реакцию (после первой он
     * `destroy()`), поэтому среди ВСЕХ совпавших реакций всех его запросов выбираем ОДНУ — с максимальным
     * `weight` (случайно среди равных). Так рост и усиление одной молекулы, приходящие разными запросами,
     * наконец конкурируют в одном месте (см. docs/molecule-graph.md, §6, «рост vs усиление»).
     *
     * 1 - для каждого запроса гоняем реагенты по всем правилам, отбираем возможные;
     * 2 - победитель запроса (max weight, тай-брейк случайно) СРАЗУ производится через produce() —
     *     пока поля правила свежие после matches(); иначе стохастический matches() (напр. chance() у
     *     распадов) при повторном матче перебросил бы кубик;
     * 3 - среди победителей всех запросов берём исход с наибольшим weight.
     *
     * produce() без побочек (spawn/updateState — отложенные лямбды, исполняет World только для
     * применённого исхода), поэтому исходы проигравших запросов безвредно отбрасываются.
     */
    fun resolve(requestsOfOneInitiator: List<List<Entity>>): ReactionOutcome? {
        var best: Pair<ReactionOutcome, Float>? = null
        for (reagents in requestsOfOneInitiator) {
            val applicableRules = rules.filter { it.matches(reagents) }
            if (applicableRules.isEmpty()) continue
            val weighted = applicableRules.map { it to it.weight() }
            val maxWeight = weighted.maxOf { it.second }
            // Отбираем правила с максимальным весом и выбираем случайное из них
            val chosenRule = weighted.filter { it.second == maxWeight }.map { it.first }.random(entityGenerator.random)
            val outcome = chosenRule.produce()   // produce СРАЗУ, пока поля правила свежие
            if (best == null || maxWeight > best.second) best = outcome to maxWeight
        }
        return best?.first
    }
}