package com.n2.moon.tower.contracts


import com.n2.moon.tower.states.TowerState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.utils.sumCash
/**
 * This is where you'll add the contract code which defines how the [TowerState] behaves. Look at the unit tests in
 * [TowerContractTests] for instructions on how to complete the [TowerContract] class.
 */
class TowerContract : Contract {
    companion object {
        @JvmStatic
        val TOWER_CONTRACT_ID = "com.n2.moon.tower.contracts.TowerContract"
    }

    /**
     * Add any commands required for this contract as classes within this interface.
     * It is useful to encapsulate your commands inside an interface, so you can use the [requireSingleCommand]
     * function to check for a number of commands which implement this interface.
     */
    interface Commands : CommandData {

        class InstallTower : TypeOnlyCommandData(), Commands
        class ProposeTowerRentalAgreement : TypeOnlyCommandData(), Commands
        class AgreeTowerRentalAgreement : TypeOnlyCommandData(), Commands
        class RejectTowerRentalAgreement : TypeOnlyCommandData(), Commands

    }

    /**
     * The contract code for the [TowerContract].
     * The constraints are self documenting so don't require any additional explanation.
     */
    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<Commands>()
        when (command.value) {
            is Commands.InstallTower -> requireThat {
                "No inputs should be consumed when issuing an Tower." using (tx.inputs.isEmpty())
                "Only one output state should be created when issuing an Tower." using (tx.outputs.size == 1)
                val iou = tx.outputsOfType<TowerState>().single()
                "A newly issued Tower must have a positive amount." using (iou.amount.quantity > 0)
                "The lender and borrower cannot have the same identity." using (iou.borrower != iou.lender)
                "Both lender and borrower together only may sign Tower issue transaction." using
                        (command.signers.toSet() == iou.participants.map { it.owningKey }.toSet())
            }
            is Commands.ProposeTowerRentalAgreement -> requireThat {
                "An Tower transfer transaction should only consume one input state." using (tx.inputs.size == 1)
                "An Tower transfer transaction should only create one output state." using (tx.outputs.size == 1)
                val input = tx.inputsOfType<TowerState>().single()
                val output = tx.outputsOfType<TowerState>().single()
                "Only the lender property may change." using (input == output.withNewLender(input.lender))
                "The lender property must change in a transfer." using (input.lender != output.lender)
                "The borrower, old lender and new lender only must sign an Tower transfer transaction" using
                        (command.signers.toSet() == (input.participants.map { it.owningKey }.toSet() `union`
                                output.participants.map { it.owningKey }.toSet()))
            }
            is Commands.AgreeTowerRentalAgreement -> {
                // Check there is only one group of IOUs and that there is always an input IOU.
                val towers = tx.groupStates<TowerState, UniqueIdentifier> { it.linearId }.single()
                requireThat { "There must be one input IOU." using (towers.inputs.size == 1) }
                // Check there are output cash states.
                val cash = tx.outputsOfType<Cash.State>()
                requireThat { "There must be output cash." using (cash.isNotEmpty()) }
                // Check that the cash is being assigned to us.
                val inputIou = towers.inputs.single()
                val acceptableCash = cash.filter { it.owner == inputIou.lender }
                requireThat { "Output cash must be paid to the lender." using (acceptableCash.isNotEmpty()) }
                // Sum the cash being sent to us (we don't care about the issuer).
                val sumAcceptableCash = acceptableCash.sumCash().withoutIssuer()
                val amountOutstanding = inputIou.amount - inputIou.paid
                requireThat { "The amount settled cannot be more than the amount outstanding." using (amountOutstanding >= sumAcceptableCash) }
                // Check to see if we need an output IOU or not.
                if (amountOutstanding == sumAcceptableCash) {
                    // If the IOU has been fully settled then there should be no IOU output state.
                    requireThat { "There must be no output IOU as it has been fully settled." using (towers.outputs.isEmpty()) }
                } else {
                    // If the IOU has been partially settled then it should still exist.
                    requireThat { "There must be one output IOU." using (towers.outputs.size == 1) }
                    // Check only the paid property changes.
                    val outputIou = towers.outputs.single()
                    requireThat { "Only the paid amount can change." using (inputIou.copy(paid = outputIou.paid) == outputIou)}

                }
                requireThat {
                    "Both lender and borrower together only must sign the IOU settle transaction." using
                            (command.signers.toSet() == inputIou.participants.map { it.owningKey }.toSet())
                }
            }
        }
    }
}