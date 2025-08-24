package maratmingazovr.ai.carsonella.chemistry.sub_atoms

import kotlinx.coroutines.flow.MutableStateFlow
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.EntityState
import maratmingazovr.ai.carsonella.chemistry.behavior.DeathNotifiable
import maratmingazovr.ai.carsonella.chemistry.behavior.EnvironmentAware
import maratmingazovr.ai.carsonella.chemistry.behavior.EnvironmentSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.LogWritable
import maratmingazovr.ai.carsonella.chemistry.behavior.LoggingSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.NeighborsAware
import maratmingazovr.ai.carsonella.chemistry.behavior.NeighborsSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.OnDeathSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.ReactionRequestSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.ReactionRequester


interface SubAtomState<State: SubAtomState<State>> : EntityState<State>

interface SubAtom<State: SubAtomState<State>> :
    Entity<State>,
    DeathNotifiable,
    NeighborsAware,
    ReactionRequester,
    EnvironmentAware,
    LogWritable

abstract class AbstractSubAtom<State : SubAtomState<State>>(
    initialState: State,
) : SubAtom<State>,
    DeathNotifiable by OnDeathSupport(), // теперь атомы во время смерти могут оповещаться мир об этом
    NeighborsAware by NeighborsSupport(),
    ReactionRequester by ReactionRequestSupport(),
    EnvironmentAware by EnvironmentSupport(),
    LogWritable by LoggingSupport()
{

    protected var state = MutableStateFlow(initialState)
    override fun state() = state

}