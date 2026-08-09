package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.Vec2D
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
 * Усиление связи: связь между двумя НЕнасыщенными атомами усиливается 1→2→3
 * (O–O → O=O, N–N → N=N → N≡N). Так рождаются кратные связи.
 *
 * ТОЛЬКО ПО КЛИКУ игрока: какую связь усилить, выбирает он — кликом по самой связи выбранной молекулы
 */
class BondStrengthening(
    private val entityGenerator: IEntityGenerator,
) : ForcedReactionRule {
    override val id = "BondStrengthening"

    private data class Match(val molecule: Molecule, val bond: MoleculeBond) : MatchedData

    // Связь берём из выбора игрока.
    override fun matches(reagents: List<Entity>, selection: ReactionSelection.Forced): MatchedData? {
        val choice = selection as? ReactionSelection.StrengthenBond ?: return null   // чужой выбор — не наш
        if (reagents.size != 1) return null   // форс приходит self-запросом (World.requestMoleculeAction)
        val subject = reagents.first() as? Molecule ?: return null
        if (!subject.alive) return null
        if (subject.getEnvironment().getEnvTemperature() == TemperatureMode.Star) return null   // в звезде молекул нет
        return Match(subject, choice.bond)
    }

    // Молекула реакцию ПЕРЕЖИВАЕТ: состав не изменился, изменилась только кратность связи:  (O–O → O=O → O≡O),
    override fun produce(match: MatchedData): ReactionOutcome {
        val (molecule, bond) = match as Match
        val atom1 = molecule.atom(bond.localId1)
        val atom2 = molecule.atom(bond.localId2)
        val env = molecule.getEnvironment()

        // Усиление ЭКЗОТЕРМИЧНО: высвобождаем прирост энергии связи E(k+1)−E(k) фотоном (как при образовании).
        // E(k) связь несёт в себе (кеш графа), за E(k+1) идём в каталог — связи такой кратности ещё нет.
        val hi = BondEnergy.of(atom1.isotope, atom2.isotope, bond.order + 1)
        val lo = bond.energy
        val released = if (hi != null && lo != null) hi - lo else null

        val spawn = mutableListOf<() -> Entity>()
        if (released != null && released > 0f) {
            // Энергию высвободила СВЯЗЬ — фотон и рождается на ней, посередине между концами. Центра у
            // молекулы нет, да он бы и не был тем местом, где эта энергия появилась.
            val p1 = atom1.kinematics.position
            val p2 = atom2.kinematics.position
            val photonPosition = Position((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
            val photonDirection = acrossBond(p1, p2)
            spawn += {
                // Фотон уносит прирост энергии связи и УЛЕТАЕТ
                entityGenerator.createEntity(Element.PHOTON, photonPosition, photonDirection,
                    MAX_VELOCITY, energy = released, environment = env, electrons = 0)
            }
        }

        return ReactionOutcome(
            spawn = spawn,
            updateState = listOf(StateUpdate(molecule) { molecule.strengthenBond(bond) }),
        )
    }

    /**
     * Куда улетает фотон, рождённый в середине связи: ПОПЕРЁК неё, в случайную из двух сторон. Вдоль связи
     * он нырнул бы в один из её концов и мог быть поглощён обратно (PhotoDissociation ловит фотоны по
     * радиусу атома), а поперёк каждый шаг уводит его от обоих концов сразу.
     *
     * Совпавшие атомы — у связи нет направления, отдаём любое.
     */
    private fun acrossBond(p1: Position, p2: Position): Vec2D {
        val axis = Vec2D(p2.x - p1.x, p2.y - p1.y)
        if (axis.length() < 1e-6f) return randomDirection(entityGenerator.random)
        val unit = axis.normalized()
        return if (entityGenerator.random.nextBoolean()) Vec2D(-unit.y, unit.x) else Vec2D(unit.y, -unit.x)
    }
}