package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.registry.AtomElement
import maratmingazovr.ai.carsonella.randomDirection
import kotlin.random.Random

/** Куда положить фотон, высвобожденный связью, и куда его пустить. */
internal class BondPhoton(val position: Position, val direction: Vec2D)

/**
 * Фотон, которого высвободила связь между атомами [p1] (радиуса [r1]) и [p2] (радиуса [r2]): образование,
 * рост, усиление, замыкание кольца, сброс колебательной энергии. Место рождения — сама связь, а не «центр»
 * молекулы: центра у неё нет, да и энергия появилась именно на связи.
 *
 * Летит ПОПЕРЁК связи, в случайную из двух сторон, и оттуда же отступает на радиус атома плюс свой
 * собственный. Обе части нужны, чтобы фотон не был поглощён обратно (`PhotoDissociation` и
 * `MolecularPhotoIonization` ловят фотоны по радиусу атома, а энергии у него ровно на эту связь):
 *  - ОТСТУП — потому что у только что образованной связи концы стоят ближе радиуса атома (связь возникает
 *    на d < 42 при радиусе 30), и её середина лежит ВНУТРИ обоих атомов;
 *  - ПОПЕРЁК — потому что дальше каждый шаг уводит фотон от обоих концов сразу, тогда как вдоль связи он
 *    нырнул бы в один из них.
 *
 * Совпавшие атомы — у связи нет направления, отступаем в любую сторону.
 */
internal fun bondPhoton(p1: Position, r1: Float, p2: Position, r2: Float, random: Random): BondPhoton {
    val axis = Vec2D(p2.x - p1.x, p2.y - p1.y)
    val across = if (axis.length() < 1e-6f) {
        randomDirection(random)
    } else {
        val unit = axis.normalized()
        if (random.nextBoolean()) Vec2D(-unit.y, unit.x) else Vec2D(unit.y, -unit.x)
    }
    val midpoint = Position((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
    val clearance = maxOf(r1, r2) + AtomElement.PHOTON.details.radius
    return BondPhoton(midpoint.addVelocity(across * clearance), across)
}