package maratmingazovr.ai.carsonella.chemistry.behavior

import maratmingazovr.ai.carsonella.EnvironmentDefault
import maratmingazovr.ai.carsonella.IEnvironment

interface EnvironmentAware {
    fun setEnvironment(environment: IEnvironment)
    fun getEnvironment(): IEnvironment
}

class EnvironmentSupport : EnvironmentAware {

    private var environment: IEnvironment =  EnvironmentDefault()


    override fun setEnvironment(environment: IEnvironment) {
        this.environment = environment
    }

    override fun getEnvironment() = environment
}