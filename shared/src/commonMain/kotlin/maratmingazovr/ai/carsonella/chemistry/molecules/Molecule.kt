package maratmingazovr.ai.carsonella.chemistry.molecules

import kotlinx.coroutines.flow.MutableStateFlow
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.EntityState
import maratmingazovr.ai.carsonella.chemistry.behavior.*


interface MoleculeState<State: MoleculeState<State>> : EntityState<State>

interface Molecule<State: MoleculeState<State>> :
    Entity<State>,
    DeathNotifiable,
    NeighborsAware,
    ReactionRequester

abstract class AbstractMolecule<State : MoleculeState<State>>(
    initialState: State,
) : Molecule<State>,
    DeathNotifiable by OnDeathSupport(),
    NeighborsAware by NeighborsSupport(),
    ReactionRequester by ReactionRequestSupport()
{
    protected val state = MutableStateFlow(initialState)
    override fun state() = state
}

enum class Symbol { H2 }