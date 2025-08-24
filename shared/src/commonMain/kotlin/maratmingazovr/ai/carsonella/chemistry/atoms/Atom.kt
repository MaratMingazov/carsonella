package maratmingazovr.ai.carsonella.chemistry.atoms

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

interface AtomState<State: AtomState<State>> : EntityState<State> {
    fun covalentRadius(): Float? // Если атом может образовывать ковалентную связь, у него есть ковалентный радиус.
}

interface Atom<State: AtomState<State>> :
    Entity<State>,
    DeathNotifiable,
    NeighborsAware,
    ReactionRequester,
    EnvironmentAware,
    LogWritable

abstract class AbstractAtom<State : AtomState<State>>(
    initialState: State,
) : Atom<State>,
    DeathNotifiable by OnDeathSupport(), // теперь атомы во время смерти могут оповещаться мир об этом
    NeighborsAware by NeighborsSupport(), // теперь атомы могут получать информацию о других объектах мира
    ReactionRequester by ReactionRequestSupport(), // теперь атомы могут вступать в реакцию с другими объектами мира
    EnvironmentAware by EnvironmentSupport(),
    LogWritable by LoggingSupport()
{

    protected val state = MutableStateFlow(initialState)
    override fun state() = state
}

