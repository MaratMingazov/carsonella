package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.MOLECULE_RADIUS
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule
import maratmingazovr.ai.carsonella.chemistry.graph.BondEnergy
import maratmingazovr.ai.carsonella.randomDirection

/**
 * Образование ковалентной связи (§6, Шаг 3a): два близких нейтральных лёгких атома со свободными
 * валентными слотами → одна двухатомная молекула.
 *
 * ВЫЧИСЛЯЕМОЕ правило, а не попарная таблица: годится для любой пары лёгких атомов. Идентичность
 * продукта — его граф, не enum-константа. Многоатомные реагенты
 * (атом+молекула → вода) — следующий шаг (3b).
 */
class CovalentBondFormation(
    private val entityGenerator: IEntityGenerator,
) : ReactionRule {
    override val id = "CovalentBond"

    private data class Match(val atom1: Atom, val atom2: Atom) : MatchedData

    override fun matches(reagents: List<Entity>): MatchedData? {
        if (reagents.size < 2) return null

        val first = bondableAtom(reagents.first()) ?: return null
        // Внутри звезды слишком горячо — молекулы не образуются.
        if (first.getEnvironment().getEnvTemperature() == TemperatureMode.Star) return null

        val firstPosition = first.state().value.kinematics.position
        val firstRadius = first.radius

        val (second, distanceSquare) = reagents
            .drop(1)
            .mapNotNull { bondableAtom(it) }
            .filter { it.getEnvironment() === first.getEnvironment() }   // оба в одной среде
            .map { it to it.state().value.kinematics.position.distanceSquareTo(firstPosition) }
            .minByOrNull { it.second }
            ?: return null

        val secondRadius = second.radius
        return if (distanceSquare < firstRadius * secondRadius * 2f) {
            Match(first, second)
        } else {
            null
        }
    }

    // Атом, способный на ковалентную связь: живой, нейтральный лёгкий атом со свободным слотом. Иначе null.
    // Проверка класса заменяет прежний тег ElementType: частицы, звезда и молекулы — не Atom.
    private fun bondableAtom(entity: Entity): Atom? {
        val atom = entity as? Atom ?: return null
        val state = atom.state().value
        if (!state.alive) return null
        if (atom.electrons != atom.element.details.p) return null   // только нейтральные (есть электроны для общей пары)
        if (atom.element.valence(atom.electrons) == 0) return null  // нет свободного слота (благородный/тяжёлый)
        return atom
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (atom1, atom2) = match as Match
        val p1 = atom1.state().value.kinematics.position
        val p2 = atom2.state().value.kinematics.position
        val midpoint = Position((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f) // временно, удалим, когда у молекулы не будет своего радиуса
        val env = atom1.getEnvironment()

        // Образование связи ЭКЗОТЕРМИЧНО: высвобождаем энергию связи фотоном (радиационная ассоциация, §6/§8).
        // Так сохраняется энергия, и этот фотон дальше может фото-ионизировать/диссоциировать соседей.
        val bondEnergy = BondEnergy.of(atom1.element, atom2.element, order = 1)
        val spawn = mutableListOf(
            { entityGenerator.createMolecule(atom1, atom2, env) },
        )
        if (bondEnergy != null && bondEnergy > 0f) {
            spawn += {
                val photonVelocity = MAX_VELOCITY
                val photonDirection = randomDirection(entityGenerator.random)
                val offset = MOLECULE_RADIUS + Element.PHOTON.details.radius // нужно выйти за радиус атома
                val photonPosition = midpoint.addVelocity(photonDirection * offset)
                entityGenerator.createEntity(Element.PHOTON, photonPosition, photonDirection, photonVelocity, energy = bondEnergy, environment = env, electrons = 0)
            }
        }

        return ReactionOutcome(
            consumed = listOf(atom1, atom2),
            spawn = spawn,
        )
    }
}