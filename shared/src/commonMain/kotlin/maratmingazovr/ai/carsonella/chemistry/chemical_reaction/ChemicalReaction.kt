package maratmingazovr.ai.carsonella.chemistry.chemical_reaction

import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.HYDROGEN
import maratmingazovr.ai.carsonella.chemistry.Element.OXYGEN_16
import maratmingazovr.ai.carsonella.chemistry.Element.H2
import maratmingazovr.ai.carsonella.chemistry.Element.O2
import maratmingazovr.ai.carsonella.chemistry.Element.H2O
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.AlphaDecay
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.AlphaProtonReaction
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.Annihilation
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.BetaMinusDecay
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.BetaPlusDecay
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.PhotoIonization
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StarAlphaGammaReaction
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StarAlphaNeutronReaction
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StarNeutronGammaReaction
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StarNeutronAlphaReaction
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StarNeutronProtonReaction
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StarProtonCaptureReaction
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StarEmission
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StarCarbonBurning
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StarOxygenBurning
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StarPPChain
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StarPhotodisintegration
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.RecombinationReaction
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.AtomPlusAtomToMolecule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.SpontaneousEmission
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules.PhotoDissociation
import kotlin.random.Random


interface IEntityGenerator {
    val random: Random
    fun createEntity(element: Element, position: Position, direction: Vec2D, velocity: Float, energy: Float, environment: IEnvironment, electrons: Int): Entity<*>
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

        // Реакции атомов
        AtomPlusAtomToMolecule(entityGenerator, HYDROGEN, HYDROGEN, H2, 4.5f),
        AtomPlusAtomToMolecule(entityGenerator, OXYGEN_16, OXYGEN_16, O2),
        AtomPlusAtomToMolecule(entityGenerator, OXYGEN_16, H2, H2O),

    )

    /**
     * 1 - Прогоняем наши реагенты по всему списку правил химических реакций.
     *     Определяем какие реакции в принципе возможны
     * 2 - Если ни одна реакция невозможна, возвращаем null
     * 3 - Если нашли несколько возможных реакций, то определяем какая из них наиболее вероятна
     * 4 - Выполняем химическую реакцию и возвращаем результат
     */
    fun resolve(reagents: List<Entity<*>>): ReactionOutcome? {
        val applicableRules = rules.filter { it.matches(reagents) }
        if (applicableRules.isEmpty()) return null
        // Сначала вычисляем веса для всех подходящих правил
        val weighted = applicableRules.map { it to it.weight() }
        // Находим максимальный вес
        val maxWeight = weighted.maxOf { it.second }
        // Отбираем все правила с максимальным весом
        val topRules = weighted.filter { it.second == maxWeight }.map { it.first }
        // Выбираем случайное из них
        val chosenRule = topRules.random(entityGenerator.random)
        // val chosenRule = applicableRules.map { it to it.weight() }.maxBy { it.second }.first
        return chosenRule.produce()
    }
}