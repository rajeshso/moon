package com.n2.moon.tower.states

import com.n2.moon.tower.contracts.TowerRentalContract
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import java.util.*

/**
 * This is where you'll add the definition of your state object. Look at the unit tests in [TowerStateTests] for
 * instructions on how to complete the [TowerRentalProposalState] class.
 *
 * Remove the "val data: String = "data" property before starting the [TowerRentalProposalState] tasks.
 */
@BelongsToContract(TowerRentalContract::class)
data class TowerRentalProposalState(val amount: Amount<Currency>,
                                    val proposerParty: Party,
                                    val agreementParty: Party,
                                    val paid: Amount<Currency> = Amount(0, amount.token),
                                    override val linearId: UniqueIdentifier = UniqueIdentifier()): LinearState {
    /**
     *  This property holds a list of the nodes which can "use" this state in a valid transaction. In this case, the
     *  lender or the borrower.
     */
    override val participants: List<Party> get() = listOf(proposerParty, agreementParty)

    /**
     * Helper methods for when building transactions for settling and transferring Towers.
     * - [pay] adds an amount to the paid property. It does no validation.
     * - [withNewLender] creates a copy of the current state with a newly specified lender. For use when transferring.
     */
    fun pay(amountToPay: Amount<Currency>) = copy(paid = paid.plus(amountToPay))
    fun withNewLender(newLender: Party) = copy(proposerParty = newLender)
}