package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
import maratmingazovr.ai.carsonella.chemistry.MoleculeBond
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.ReactionSelection
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ForcedReactionRule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StateUpdate
import maratmingazovr.ai.carsonella.chemistry.graph.BondEnergy
import maratmingazovr.ai.carsonella.randomDirection

/**
 * Усиление связи (§6, Шаг 3c): связь между двумя НЕнасыщенными атомами усиливается 1→2→3
 * (O–O → O=O, N–N → N=N → N≡N). Так рождаются кратные связи.
 *
 * ТОЛЬКО ПО КЛИКУ игрока ([ForcedReactionRule]): какую связь усилить, выбирает он — кликом по самой
 * связи выбранной молекулы, и его выбор приезжает в [ReactionSelection.StrengthenBond]. В эмёрджентном
 * списке правил этого правила НЕТ, само оно не срабатывает.
 *
 * Почему не эмёрджентно: спонтанное усиление — это ассоциация без активационного барьера, а барьеров
 * модель пока не знает (см. docs/molecule-graph.md). Цена решения: O₂ и N₂ сами собой не собираются —
 * `CovalentBondFormation` даёт только одинарную O–O, двойную делает игрок. Вернуть эмёрджентность =
 * реализовать ещё и [maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule]
 * (выбор связи там должен идти по максимальному выигрышу энергии, а не по первой подходящей).
 */
class BondStrengthening(
    private val entityGenerator: IEntityGenerator,
) : ForcedReactionRule {
    override val id = "BondStrengthening"

    private data class Match(val molecule: Molecule, val bond: MoleculeBond) : MatchedData

    /**
     * Связь берём прямо из выбора игрока. Снимок не устаревает, но уже НЕ потому, что граф неизменен —
     * усиление правит его на месте ([Molecule.strengthenBond]). Держится это на двух вещах: за тик
     * молекула получает ровно одно усиление (запросы сгруппированы по инициатору, resolve применяет один
     * исход), а `localId` концов усиление не двигает — меняется только кратность ребра. Умерла между
     * кликом и resolve → отсекает `alive`.
     *
     * Единственное, что в снимке всё же может разойтись с графом — `bond.order`, и по нему `produce`
     * считает энергию фотона. Пока усиление за тик одно, разойтись негде.
     */
    override fun matches(reagents: List<Entity>, selection: ReactionSelection.Forced): MatchedData? {
        val choice = selection as? ReactionSelection.StrengthenBond ?: return null   // чужой выбор — не наш
        if (reagents.size != 1) return null   // форс приходит self-запросом (World.requestMoleculeAction)
        val subject = reagents.first() as? Molecule ?: return null
        if (!subject.alive) return null
        if (subject.getEnvironment().getEnvTemperature() == TemperatureMode.Star) return null   // в звезде молекул нет
        return Match(subject, choice.bond)
    }

    /**
     * Молекула реакцию ПЕРЕЖИВАЕТ: состав не изменился, изменилась только кратность связи, поэтому это та
     * же сущность — исход мутирует её через [StateUpdate], как ионизация мутирует атом, а не рождает новый.
     * Отсюда же бонус: id сохраняется, и выделение молекулы у игрока не слетает — усиливать можно кликами
     * подряд (O–O → O=O → O≡O).
     */
    override fun produce(match: MatchedData): ReactionOutcome {
        val (molecule, bond) = match as Match
        val kinematics = molecule.kinematics
        val env = molecule.getEnvironment()

        // Усиление ЭКЗОТЕРМИЧНО: высвобождаем прирост энергии связи E(k+1)−E(k) фотоном (как при образовании).
        // E(k) связь несёт в себе (кеш графа), за E(k+1) идём в каталог — связи такой кратности ещё нет.
        val hi = BondEnergy.of(bond.atom1.structure.isotope, bond.atom2.structure.isotope, bond.order + 1)
        val lo = bond.energy
        val released = if (hi != null && lo != null) hi - lo else null

        val spawn = mutableListOf<() -> Entity>()
        if (released != null && released > 0f) {
            spawn += {
                // Фотон уносит прирост энергии связи и УЛЕТАЕТ (скорость 40, как в SpontaneousEmission): за тик
                // покидает радиус активации, иначе PhotoDissociation мог бы поймать его и распустить молекулу —
                // тот же цикл образование↔распад, что и при росте/образовании связи.
                entityGenerator.createEntity(Element.PHOTON, kinematics.position, randomDirection(entityGenerator.random),
                    MAX_VELOCITY, energy = released, environment = env, electrons = 0)
            }
        }

        return ReactionOutcome(
            spawn = spawn,
            updateState = listOf(StateUpdate(molecule) { molecule.strengthenBond(bond) }),
        )
    }
}