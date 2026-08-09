package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.randomDirection
import kotlin.random.Random

/**
 * Где рождается фотон, которого высвободила связь, и куда он летит. Общее для правил, которые связь
 * создают или усиливают ([BondStrengthening], [RingClosure]) и после которых молекула ОСТАЁТСЯ жива:
 * центра у неё нет, а место рождения энергии — сама связь.
 */

// На связи, посередине между её концами.
internal fun bondMidpoint(p1: Position, p2: Position) = Position((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)

/**
 * ПОПЕРЁК связи, в случайную из двух сторон. Вдоль связи фотон нырнул бы в один из её концов и мог быть
 * поглощён обратно (PhotoDissociation и MolecularPhotoIonization ловят фотоны по радиусу атома), а поперёк
 * каждый шаг уводит его от обоих концов сразу.
 *
 * Совпавшие атомы — у связи нет направления, отдаём любое.
 */
internal fun acrossBond(p1: Position, p2: Position, random: Random): Vec2D {
    val axis = Vec2D(p2.x - p1.x, p2.y - p1.y)
    if (axis.length() < 1e-6f) return randomDirection(random)
    val unit = axis.normalized()
    return if (random.nextBoolean()) Vec2D(-unit.y, unit.x) else Vec2D(unit.y, -unit.x)
}