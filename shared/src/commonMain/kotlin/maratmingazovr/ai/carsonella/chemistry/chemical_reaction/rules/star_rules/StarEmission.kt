package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.star_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Star
import maratmingazovr.ai.carsonella.chemistry.behavior.Movable
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StateUpdate
import maratmingazovr.ai.carsonella.randomDirection

// Звезда либо генерирует внутри себя протон с электроном
// Либо при большой концентрации  излучает элементы наружку в космос
class StarEmission (
    private val entityGenerator: IEntityGenerator,
) : StarReactionRule() {
    override val id = "StarEmission"

    /**
     * Две ветки правила: [absorbReagents] непусто → ПОГЛОЩЕНИЕ (соседи снаружи втягиваются в звезду);
     * пусто → генерация/выброс, и тогда работаем со списком живых детей [entityReagents].
     */
    private data class Match(
        val star: Star,
        val absorbReagents: List<Entity>,
        val entityReagents: List<Entity>,
    ) : MatchedData

    override fun matchesStar(star: Star, neighbors: List<Entity>): MatchedData? {
        // Поглощение: запрос вида [звезда + соседи снаружи] — втягиваем их сразу, без chance.
        val external = neighbors.filter { it.alive && it.getEnvironment() !== star }
        if (external.isNotEmpty()) {
            return Match(star, absorbReagents = external, entityReagents = listOf())
        }

        // Иначе запрос [звезда] — ветка генерации/выброса (редкое событие).
        if (!chance(0.012f, entityGenerator.random)) return null

        val children = star
            .getEnvChildren()
            .filter { reagent -> reagent.alive }
        return Match(star, absorbReagents = listOf(), entityReagents = children)
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (star, absorbReagents, entityReagents) = match as Match

        // Поглощение: внешние реагенты у поверхности становятся детьми звезды (updateMyEnvironment(star)).
        // alive-гард — на случай, если реагент уже потреблён другим запросом в этом же тике.
        if (absorbReagents.isNotEmpty()) {
            return ReactionOutcome(
                updateState = absorbReagents.map { r ->
                    StateUpdate(r) {
                        if (r.alive) r.updateMyEnvironment(
                            star
                        )
                    }
                },
            )
        }

        /*
        Когда концентрация элементов в звезде повышается, она начинает излучить их в космос
         */
        if (entityReagents.size < 20) {
            val resultElement =  if (!chance(0.5f, entityGenerator.random))  Element.HYDROGEN else Element.ELECTRON
            return ReactionOutcome(
                spawn = listOf {
                    entityGenerator.createEntity(
                        resultElement,
                        star.kinematics.position,
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
            val updateList = mutableListOf<StateUpdate>()
            if (reagent != null) {
                updateList += StateUpdate(reagent) {
                    // Ребёнок звезды — атом, частица или молекула; все они Movable, но список у нас
                    // из Entity, поэтому движение достаём отдельно.
                    val movement = reagent as Movable
                    val center = star.kinematics.position
                    val pos = movement.kinematics.position
                    // Упрощённый выброс: телепортируем ребёнка за кольцо поглощения (radius + 10),
                    // чтобы звезда не засосала его обратно тем же тиком. Нормальный выброс (импульс) — позже.
                    val fromCenter = Vec2D(pos.x - center.x, pos.y - center.y)
                    // Ребёнок ровно в центре звезды — направления «наружу» нет, берём случайное.
                    val outward =
                        if (fromCenter.length() < 1e-6f) randomDirection(entityGenerator.random) else fromCenter.normalized()
                    val ejectDistance = star.radius + 20f
                    val ejectPoint = Position(center.x + outward.x * ejectDistance, center.y + outward.y * ejectDistance)
                    movement.moveBy(Vec2D(ejectPoint.x - pos.x, ejectPoint.y - pos.y))
                    val mass = movement.mass
                    movement.applyForce(outward.times(mass * 2f))
                    reagent.updateMyEnvironment(star.getEnvironment())
                }
            }
            return ReactionOutcome(updateState = updateList)
        }

    }
}