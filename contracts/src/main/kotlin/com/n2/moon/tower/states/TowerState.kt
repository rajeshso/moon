package com.n2.moon.tower.states

import com.n2.moon.tower.contracts.TowerRentalContract
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import java.util.*

/**
 * This is where you'll add the definition of your state object. Look at the unit tests in [TowerState] for
 * instructions on how to complete the [TowerState] class.
 *
 * Remove the "val data: String = "data" property before starting the [TowerState] tasks.
 */
@BelongsToContract(TowerRentalContract::class)
data class TowerState(val latitude: String,
                      val longitude: String,
                      val height: String,
                      val spec: String,
                      val totalSlots: Int,
                      val rentalAmount: Amount<Currency>,
                      val towerInfrastructureProvider: Party,
                      val bookedSlots: Int = 0,
                      val mobileNetworkOperators: List<Party> = emptyList(),
                      override val linearId: UniqueIdentifier = UniqueIdentifier()): LinearState {
    /**
     *  This property holds a list of the nodes which can "use" this state in a valid transaction. In this case, the
     *  proposer or the agreementParty.
     */
    override val participants: List<Party> get() = listOf(towerInfrastructureProvider)

    /**
     * Helper methods for when building transactions for settling and transferring Towers.
     * - [withNewMno] creates a copy of the current state with a newly specified mno. For use when onboarding new mno into the tower.
     */
    fun withNewMno(newMno: Party) = copy(mobileNetworkOperators = mobileNetworkOperators+newMno, bookedSlots=bookedSlots+1)
}