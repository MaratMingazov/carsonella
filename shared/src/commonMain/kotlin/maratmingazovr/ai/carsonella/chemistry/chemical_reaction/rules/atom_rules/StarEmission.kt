package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.randomDirection
import kotlin.collections.List

// Звезда либо генерирует внутри себя протон с электроном
// Либо при большой концентрации  излучает элементы наружку в космос
class StarEmission (
    private val entityGenerator: IEntityGenerator,
) : AtomReactionRule() {
    override val id = "StarEmission"

    /**
     * Две ветки правила: [absorbReagents] непусто → ПОГЛОЩЕНИЕ (соседи снаружи втягиваются в звезду);
     * пусто → генерация/выброс, и тогда работаем со списком живых детей [entityReagents].
     */
    private data class Match(
        val star: Entity,
        val absorbReagents: List<Entity>,
        val entityReagents: List<Entity>,
    ) : MatchedData

    override fun matchesAtoms(reagents: List<Entity>): MatchedData? {
        if (reagents.isEmpty()) return null

        val first = reagents.first()
        // species в локальный val → smart-cast к Elemental ниже (через Entity компилятор сам этого не знает).
        val species = first.state().value.species
        if (species !is Species.Atomic) return null
        if (species.element != Element.Star) return null
        if (!first.state().value.alive) return null

        // Поглощение: запрос вида [звезда + соседи снаружи] — втягиваем их сразу, без chance.
        val external = reagents.drop(1).filter { it.state().value.alive && it.getEnvironment() !== first }
        if (external.isNotEmpty()) {
            return Match(first, absorbReagents = external, entityReagents = listOf())
        }

        // Иначе запрос [звезда] — ветка генерации/выброса (редкое событие).
        if (!chance(0.012f, entityGenerator.random)) return null

        val children = first
            .getEnvChildren()
            .filter { reagent -> reagent.state().value.alive }
        return Match(first, absorbReagents = listOf(), entityReagents = children)
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (star, absorbReagents, entityReagents) = match as Match

        // Поглощение: внешние реагенты у поверхности становятся детьми звезды (updateMyEnvironment(star)).
        // alive-гард — на случай, если реагент уже потреблён другим запросом в этом же тике.
        if (absorbReagents.isNotEmpty()) {
            return ReactionOutcome(
                updateState = absorbReagents.map { r -> { if (r.state().value.alive) r.updateMyEnvironment(star) } },
                description = "$id: ${Element.Star.details.symbol} <- " +
                        absorbReagents.joinToString { it.state().value.displaySymbol },
            )
        }

        /*
        Когда концентрация элементов в звезде повышается, она начинает излучить их в космос
         */
        if (entityReagents.size < 20) {
            val resultElement =  if (!chance(0.5f, entityGenerator.random))  Element.Proton else Element.ELECTRON
            // Fuel-ветка молчит в логе: при chance(0.012) за тик это ~0.75 раз/сек на звезду —
            // лог заглушает реальные реакции (CNO, α-захват, (α,p)). Outflow-ветку логируем.
            return ReactionOutcome(
                spawn = listOf {
                    entityGenerator.createEntity(
                        resultElement,
                        star.state().value.position,
                        randomDirection(entityGenerator.random),
                        2f,
                        energy = 0f,
                        environment = star,
                        electrons = if (resultElement == Element.ELECTRON) 1 else 0,
                    )
                },
            )
        } else {
            // Звезда выбрасывает случайного живого ребёнка наружу. Раньше был хардкод p⁺/e⁻/O⁸⁺,
            // из-за которого продукты нуклеосинтеза (Li, N, Ne, Mg, Si, … вплоть до ⁵⁶Ni) застревали
            // внутри звезды и игроку не показывались.
            val reagent = entityReagents.randomOrNull(entityGenerator.random)
            val updateList = mutableListOf<() -> Unit>()
            var description = ""
            if (reagent != null) {
                updateList += {
                    val center = star.state().value.position
                    val pos = reagent.state().value.position
                    // Упрощённый выброс: телепортируем ребёнка за кольцо поглощения (radius + 10),
                    // чтобы звезда не засосала его обратно тем же тиком. Нормальный выброс (импульс) — позже.
                    var outward = Vec2D(pos.x - center.x, pos.y - center.y)
                    if (outward.length() < 1e-6f) outward = randomDirection(entityGenerator.random)
                    outward.normalizeInPlace()
                    val ejectDistance = star.state().value.radius + 20f
                    reagent.moveTo(Position(center.x + outward.x * ejectDistance, center.y + outward.y * ejectDistance))
                    // Небольшая скорость наружу: moveTo обнулил скорость, поэтому applyForce задаёт
                    // чистое направление (наружу) и величину. Сила ∝ массе → одинаковая прибавка скорости.
                    val mass = reagent.mass()
                    reagent.applyForce(outward.times(mass * 2f))
                    reagent.updateMyEnvironment(star.getEnvironment())
                }
                description = "$id: ${Element.Star.details.symbol} → ${reagent.state().value.displaySymbol}"
            }
            return ReactionOutcome(updateState = updateList, description = description)
        }

    }
}