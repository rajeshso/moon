package com.n2.moon.tower.states

import com.n2.moon.tower.contracts.TowerRentalContract
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import java.util.*

/**
 * This is where you'll add the definition of your state object. Look at the unit tests in [TowerRentalProposalState] for
 * instructions on how to complete the [TowerRentalProposalState] class.
 *
 * Remove the "val data: String = "data" property before starting the [TowerRentalProposalState] tasks.
 */
@BelongsToContract(TowerRentalContract::class)
data class TowerRentalProposalState(val rentalAmount: Amount<Currency>,
                                    val proposerParty: Party,//mno
                                    val agreementParty: Party,//tower infrastructure provider
                                    val paid: Amount<Currency> = Amount(0, rentalAmount.token),
                                    val towerState: TowerState,
                                    override val linearId: UniqueIdentifier = UniqueIdentifier()
                                    ): LinearState {
    /**
     *  This property holds a list of the nodes which can "use" this state in a valid transaction. In this case, the
     *  proposer or the agreementParty.
     */
    override val participants: List<Party> get() = listOf(proposerParty, agreementParty)

    /**
     * Helper methods for when building transactions for settling and transferring Towers.
     * - [pay] adds an amount to the paid property. It does no validation.
     * - [withNewProposer] creates a copy of the current state with a newly specified proposer. For use when transferring.
     */
    fun pay(amountToPay: Amount<Currency>) = copy(paid = paid.plus(amountToPay))
    fun withNewProposer(newProposer: Party) = copy(proposerParty = newProposer)
}