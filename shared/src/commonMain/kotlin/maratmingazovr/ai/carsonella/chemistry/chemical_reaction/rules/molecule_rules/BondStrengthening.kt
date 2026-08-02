package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
import maratmingazovr.ai.carsonella.chemistry.MolecularBond
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.ReactionSelection
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ForcedReactionRule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
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

    private data class Match(val molecule: Entity, val bond: MolecularBond) : MatchedData

    /**
     * Связь берём прямо из выбора игрока: пока сущность жива, её граф неизменен (любая перестройка
     * рождает новую сущность), поэтому `localId` концов и кратность в снимке не могут устареть.
     * Умерла между кликом и resolve → отсекает `alive`.
     */
    override fun matches(reagents: List<Entity>, selection: ReactionSelection.Forced): MatchedData? {
        val choice = selection as? ReactionSelection.StrengthenBond ?: return null   // чужой выбор — не наш
        if (reagents.size != 1) return null   // форс приходит self-запросом (World.requestMoleculeAction)
        val first = reagents.first()
        val state = first.state().value
        if (!state.alive) return null
        if (first.getEnvironment().getEnvTemperature() == TemperatureMode.Star) return null   // в звезде молекул нет
        if (state.species !is Species.Molecular) return null
        return Match(first, choice.bond)
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (molecule, bond) = match as Match
        val state = molecule.state().value
        val graph = (molecule as Molecule).graph
        val strengthened = Species.Molecular(graph.strengthenBond(bond.atom1.localId, bond.atom2.localId))
        val env = molecule.getEnvironment()

        // Усиление ЭКЗОТЕРМИЧНО: высвобождаем прирост энергии связи E(k+1)−E(k) фотоном (как при образовании).
        val hi = BondEnergy.of(bond.atom1.isotope, bond.atom2.isotope, bond.order + 1)
        val lo = BondEnergy.of(bond.atom1.isotope, bond.atom2.isotope, bond.order)
        val released = if (hi != null && lo != null) hi - lo else null

        val spawn = mutableListOf(
            { entityGenerator.createEntity(strengthened, state.centerPosition, state.direction, state.velocity, state.energy, env, state.electrons) },
        )
        if (released != null && released > 0f) {
            spawn += {
                // Фотон уносит прирост энергии связи и УЛЕТАЕТ (скорость 40, как в SpontaneousEmission): за тик
                // покидает радиус активации, иначе PhotoDissociation мог бы поймать его и распустить молекулу —
                // тот же цикл образование↔распад, что и при росте/образовании связи.
                entityGenerator.createEntity(Element.PHOTON, state.centerPosition, randomDirection(entityGenerator.random),
                    MAX_VELOCITY, energy = released, environment = env, electrons = 0)
            }
        }

        return ReactionOutcome(
            consumed = listOf(molecule),
            spawn = spawn,
            description = "$id: ${molecule.displaySymbol} связь ${bond.atom1.localId}-${bond.atom2.localId} ${bond.order}→${bond.order + 1}" +
                (released?.let { " + γ[${it}eV]" } ?: ""),
        )
    }
}