package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.ElementType
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
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
 * продукта — его граф ([maratmingazovr.ai.carsonella.chemistry.Species.Molecular]), не enum-константа. Многоатомные реагенты
 * (атом+молекула → вода) — следующий шаг (3b).
 */
class CovalentBondFormation(
    private val entityGenerator: IEntityGenerator,
) : ReactionRule {
    override val id = "CovalentBond"

    private var atom1: Entity? = null
    private var atom2: Entity? = null

    override fun matches(reagents: List<Entity>): Boolean {
        atom1 = null
        atom2 = null
        if (reagents.size < 2) return false

        val first = reagents.first()
        if (!canBond(first)) return false
        // Внутри звезды слишком горячо — молекулы не образуются.
        if (first.getEnvironment().getEnvTemperature() == TemperatureMode.Star) return false

        val firstPosition = first.state().value.position
        val firstRadius = first.state().value.radius

        val (second, distanceSquare) = reagents
            .drop(1)
            .filter { canBond(it) }
            .filter { it.getEnvironment() === first.getEnvironment() }   // оба в одной среде
            .map { it to it.state().value.position.distanceSquareTo(firstPosition) }
            .minByOrNull { it.second }
            ?: return false

        val secondRadius = second.state().value.radius
        return if (distanceSquare < firstRadius * secondRadius * 2f) {
            atom1 = first
            atom2 = second
            true
        } else {
            false
        }
    }

    // Атом способен на ковалентную связь: живой, нейтральный лёгкий атом со свободным слотом.
    // Через Species (не через шов .element): связываем только Elemental-атомы.
    private fun canBond(entity: Entity): Boolean {
        val state = entity.state().value
        if (!state.alive) return false
        val species = state.species
        if (species !is Species.Elemental) return false              // молекулы пока не связываем (3b)
        val element = species.element
        if (element.details.type != ElementType.Atom) return false   // только атомы (не частицы/звезда/модуль)
        if (state.electrons != element.details.p) return false       // только нейтральные (есть электроны для общей пары)
        return element.valence() > 0                                 // есть свободный слот (0 → благородный/тяжёлый)
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {
        val a1 = atom1!!
        val a2 = atom2!!
        val iso1 = (a1.state().value.species as Species.Elemental).element
        val iso2 = (a2.state().value.species as Species.Elemental).element

        val (direction, velocity) = calculateNewEntityDirectionAndVelocity(a1, a2)
        val p1 = a1.state().value.position
        val p2 = a2.state().value.position
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
        val spawn = mutableListOf<() -> Entity>(
            { entityGenerator.createEntity(Species.Molecular(graph), midpoint, direction, velocity, energy, env, electrons) },
        )
        if (bondEnergy != null && bondEnergy > 0f) {
            spawn += {
                // Фотон уносит энергию связи и УЛЕТАЕТ (скорость 40, как в SpontaneousEmission): за тик он покидает
                // радиус активации молекулы. Иначе на следующем тике PhotoDissociation поймал бы его и распустил
                // молекулу обратно (энергия фотона = энергии связи = порогу распада) — бесконечный цикл
                // образование↔распад. Направление случайное (излучение изотропно).
                entityGenerator.createEntity(Element.PHOTON, midpoint, randomDirection(entityGenerator.random), 40f, energy = bondEnergy, environment = env, electrons = 0)
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