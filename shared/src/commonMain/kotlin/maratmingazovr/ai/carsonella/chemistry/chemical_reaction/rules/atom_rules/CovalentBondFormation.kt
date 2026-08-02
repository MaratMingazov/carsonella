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
import maratmingazovr.ai.carsonella.chemistry.graph.AtomNode
import maratmingazovr.ai.carsonella.chemistry.graph.Bond
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
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

        val firstPosition = first.state().value.kinematics.centerPosition
        val firstRadius = first.radius

        val (second, distanceSquare) = reagents
            .drop(1)
            .mapNotNull { bondableAtom(it) }
            .filter { it.getEnvironment() === first.getEnvironment() }   // оба в одной среде
            .map { it to it.state().value.kinematics.centerPosition.distanceSquareTo(firstPosition) }
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
        if (state.electrons != atom.element.details.p) return null   // только нейтральные (есть электроны для общей пары)
        if (atom.element.valence(state.electrons) == 0) return null  // нет свободного слота (благородный/тяжёлый)
        return atom
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (a1, a2) = match as Match
        val iso1 = a1.element
        val iso2 = a2.element

        val (direction, velocity) = calculateNewEntityDirectionAndVelocity(a1, a2)
        val p1 = a1.state().value.kinematics.centerPosition
        val p2 = a2.state().value.kinematics.centerPosition
        val midpoint = Position((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
        // Сохранение: электроны молекулы = сумма электронов реагентов (оба нейтральны → нейтральная молекула).
        val electrons = a1.state().value.electrons + a2.state().value.electrons
        val energy = a1.state().value.energy + a2.state().value.energy
        val env = a1.getEnvironment()

        // Начинаем всегда с ОДИНАРНОЙ связи (order = 1) — это корректно для любой пары. Кратность НЕ
        // вычисляем заранее (octet/valence — лишь приближение). Дальше молекула эволюционирует на
        // следующих тиках: если у атома остался свободный слот, он либо притянет ЕЩЁ атом/молекулу (3b),
        // либо, если партнёров рядом нет, УСИЛИТ эту связь до двойной/тройной (3c) — так эмёрджентно
        // получаются O=O, N≡N, а углерод расходует слоты на разных партнёров (цепи). См. §6 дока.
        val graph = MoleculeGraph(
            nodes = listOf(AtomNode(0, iso1), AtomNode(1, iso2)),
            bonds = listOf(Bond(0, 1, order = 1)),
        )

        // Образование связи ЭКЗОТЕРМИЧНО: высвобождаем энергию связи фотоном (радиационная ассоциация, §6/§8).
        // Так сохраняется энергия, и этот фотон дальше может фото-ионизировать/диссоциировать соседей.
        val bondEnergy = BondEnergy.of(iso1, iso2, order = 1)
        val spawn = mutableListOf(
            { entityGenerator.createMolecule(graph, midpoint, direction, velocity, energy, env, electrons) },
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
            consumed = listOf(a1, a2),
            spawn = spawn,
            description = "$id: ${iso1.symbol(a1.state().value.electrons)} + ${iso2.symbol(a2.state().value.electrons)} -> ${graph.formulaPretty}" +
                (bondEnergy?.let { " + γ[${it}eV]" } ?: ""),
        )
    }
}