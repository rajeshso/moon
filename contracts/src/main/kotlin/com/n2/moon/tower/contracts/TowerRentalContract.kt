package com.n2.moon.tower.contracts


import com.n2.moon.tower.states.TowerRentalProposalState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.utils.sumCash
/**
 * This is where you'll add the contract code which defines how the [TowerRentalProposalState] behaves. Look at the unit tests in
 * [TowerRentalContractTests] for instructions on how to complete the [TowerRentalContract] class.
 */
class TowerRentalContract : Contract {
    companion object {
        @JvmStatic
        val TOWER_CONTRACT_ID = "com.n2.moon.tower.contracts.TowerRentalContract"
    }

    /**
     * Add any commands required for this contract as classes within this interface.
     * It is useful to encapsulate your commands inside an interface, so you can use the [requireSingleCommand]
     * function to check for a number of commands which implement this interface.
     */
    interface Commands : CommandData {

        class ProposeTowerRentalAgreement : TypeOnlyCommandData(), Commands
        class AgreeTowerRentalAgreement : TypeOnlyCommandData(), Commands
        class RejectTowerRentalAgreement : TypeOnlyCommandData(), Commands

    }

    /**
     * The contract code for the [TowerRentalContract].
     * The constraints are self documenting so don't require any additional explanation.
     */
    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<Commands>()
        when (command.value) {
            is Commands.ProposeTowerRentalAgreement -> requireThat {
                "No inputs should be consumed when issuing an Tower." using (tx.inputs.isEmpty())
                "Only one output state should be created when issuing an Tower." using (tx.outputs.size == 1)
                val towerState = tx.outputsOfType<TowerRentalProposalState>().single()
                "A newly issued Tower must have a positive amount." using (towerState.amount.quantity > 0)
                "The proposer and agreementParty cannot have the same identity." using (towerState.agreementParty != towerState.proposerParty)
                "Both proposer and agreementParty together only may sign Tower issue transaction." using
                        (command.signers.toSet() == towerState.participants.map { it.owningKey }.toSet())
            }
            is Commands.AgreeTowerRentalAgreement -> requireThat {
                "An Tower transfer transaction should only consume one input state." using (tx.inputs.size == 1)
                "An Tower transfer transaction should only create one output state." using (tx.outputs.size == 1)
                val input = tx.inputsOfType<TowerRentalProposalState>().single()
                val output = tx.outputsOfType<TowerRentalProposalState>().single()
                "Only the proposer property may change." using (input == output.withNewProposer(input.proposerParty))
                "The proposer property must change in a transfer." using (input.proposerParty != output.proposerParty)
                "The agreementParty, old proposer and new proposer only must sign an Tower transfer transaction" using
                        (command.signers.toSet() == (input.participants.map { it.owningKey }.toSet() `union`
                                output.participants.map { it.owningKey }.toSet()))
            }
            is Commands.RejectTowerRentalAgreement -> {
                // Check there is only one group of Towers and that there is always an input Tower.
                val towers = tx.groupStates<TowerRentalProposalState, UniqueIdentifier> { it.linearId }.single()
                requireThat { "There must be one input Tower." using (towers.inputs.size == 1) }
                // Check there are output cash states.
                val cash = tx.outputsOfType<Cash.State>()
                requireThat { "There must be output cash." using (cash.isNotEmpty()) }
                // Check that the cash is being assigned to us.
                val inputTower = towers.inputs.single()
                val acceptableCash = cash.filter { it.owner == inputTower.proposerParty }
                requireThat { "Output cash must be paid to the proposer." using (acceptableCash.isNotEmpty()) }
                // Sum the cash being sent to us (we don't care about the issuer).
                val sumAcceptableCash = acceptableCash.sumCash().withoutIssuer()
                val amountOutstanding = inputTower.amount - inputTower.paid
                requireThat { "The amount settled cannot be more than the amount outstanding." using (amountOutstanding >= sumAcceptableCash) }
                // Check to see if we need an output Tower or not.
                if (amountOutstanding == sumAcceptableCash) {
                    // If the Tower has been fully settled then there should be no Tower output state.
                    requireThat { "There must be no output Tower as it has been fully settled." using (towers.outputs.isEmpty()) }
                } else {
                    // If the Tower has been partially settled then it should still exist.
                    requireThat { "There must be one output Tower." using (towers.outputs.size == 1) }
                    // Check only the paid property changes.
                    val outputTower = towers.outputs.single()
                    requireThat { "Only the paid amount can change." using (inputTower.copy(paid = outputTower.paid) == outputTower)}

                }
                requireThat {
                    "Both proposer and agreementParty together only must sign the Tower settle transaction." using
                            (command.signers.toSet() == inputTower.participants.map { it.owningKey }.toSet())
                }
            }
        }
    }
}