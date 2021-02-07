package com.n2.moon.tower.contracts

import com.n2.moon.tower.states.TowerRentalProposalState
import net.corda.core.contracts.*
import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.finance.SWISS_FRANCS
import net.corda.samples.obligation.ALICE
import net.corda.samples.obligation.BOB
import net.corda.samples.obligation.DUMMY
import net.corda.samples.obligation.MINICORP
import net.corda.testing.contracts.DummyState
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

/**
 * Practical exercise instructions for Contracts Part 1.
 * The objective here is to write some contract code that verifies a transaction to issue an [TowerRentalProposalState].
 * As with the [TowerStateTests] uncomment each unit test and run them one at a time. Use the body of the tests and the
 * task description to determine how to get the tests to pass.
 */
class ProposeTowerRentalAgreementFlowTests {
    // A pre-defined dummy command.
    class DummyCommand : TypeOnlyCommandData()
    private var ledgerServices = MockServices(listOf("com.n2.moon.tower.contracts"))

    /**
     * Task 1.
     * Recall that Commands are required to hint to the intention of the transaction as well as take a list of
     * public keys as parameters which correspond to the required signers for the transaction.
     * Commands also become more important later on when multiple actions are possible with an TowerState, e.g. Transfer
     * and Settle.
     * TODO: Add an "Issue" command to the TowerRentalContract and check for the existence of the command in the verify function.
     * Hint:
     * - For the create command we only care about the existence of it in a transaction, therefore it should subclass
     *   the [TypeOnlyCommandData] class.
     * - The command should be defined inside [TowerRentalContract].
     * - You can use the [requireSingleCommand] function on [tx.commands] to check for the existence and type of the specified command
     *   in the transaction. [requireSingleCommand] requires a generic type to identify the type of command required.
     *
     *   requireSingleCommand<REQUIRED_COMMAND>()
     *
     * - We usually encapsulate our commands around an interface inside the contract class called [Commands] which
     *   implements the [CommandData] interface. The [TowerRentalContract.Commands.ProposeTowerRentalAgreement] command itself should be defined inside the [Commands]
     *   interface as well as implement it, for example:
     *
     *     interface Commands : CommandData {
     *         class X : TypeOnlyCommandData(), Commands
     *     }
     *
     * - We can check for the existence of any command that implements [TowerRentalContract.Commands] by using the
     *   [requireSingleCommand] function which takes a type parameter.
     */
    @Test
    fun mustIncludeIssueCommand() {
        val towerState = TowerRentalProposalState(1.POUNDS, ALICE.party, BOB.party)
        ledgerServices.ledger {
            transaction {
                output(TowerRentalContract.TOWER_CONTRACT_ID,  towerState)
                command(listOf(ALICE.publicKey, BOB.publicKey), DummyCommand()) // Wrong type.
                this.fails()
            }
            transaction {
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerState)
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.ProposeTowerRentalAgreement()) // Correct type.
                this.verifies()
            }
        }
    }

    /**
     * Task 2.
     * As previously observed, issue transactions should not have any input state references. Therefore we must check to
     * ensure that no input states are included in a transaction to issue an TowerState.
     * TODO: Write a contract constraint that ensures a transaction to issue an TowerState does not include any input states.
     * Hint: use a [requireThat] block with a constraint to inside the [TowerRentalContract.verify] function to encapsulate your
     * constraints:
     *
     *     requireThat {
     *         "Message when constraint fails" using (boolean constraint expression)
     *     }
     *
     * Note that the unit tests often expect contract verification failure with a specific message which should be
     * defined with your contract constraints. If not then the unit test will fail!
     *
     * You can access the list of inputs via the [LedgerTransaction] object which is passed into
     * [TowerRentalContract.verify].
     */
    //@Disabled
    fun issueTransactionMustHaveNoInputs() {
        val towerState = TowerRentalProposalState(1.POUNDS, ALICE.party, BOB.party)
        ledgerServices.ledger {
            transaction {
                input(TowerRentalContract.TOWER_CONTRACT_ID, DummyState())
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.ProposeTowerRentalAgreement())
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerState)
                this `fails with` "No inputs should be consumed when issuing an TowerState."
            }
            transaction {
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerState)
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.ProposeTowerRentalAgreement())
                this.verifies() // As there are no input states.
            }
        }
    }

    /**
     * Task 3.
     * Now we need to ensure that only one [TowerRentalProposalState] is issued per transaction.
     * TODO: Write a contract constraint that ensures only one output state is created in a transaction.
     * Hint: Write an additional constraint within the existing [requireThat] block which you created in the previous
     * task.
     */
    @Test
    fun issueTransactionMustHaveOneOutput() {
        val towerState = TowerRentalProposalState(1.POUNDS, ALICE.party, BOB.party)
        ledgerServices.ledger {
            transaction {
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.ProposeTowerRentalAgreement())
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerState) // Two outputs fails.
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerState)
                this `fails with` "Only one output state should be created when issuing an Tower."
            }
            transaction {
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.ProposeTowerRentalAgreement())
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerState) // One output passes.
                this.verifies()
            }
        }
    }

    /**
     * Task 4.
     * Now we need to consider the properties of the [TowerRentalProposalState]. We need to ensure that an Tower should always have a
     * positive value.
     * TODO: Write a contract constraint that ensures newly issued Towers always have a positive value.
     * Hint: You will need a number of hints to complete this task!
     * - Use the Kotlin keyword 'val' to create a new constant which will hold a reference to the output Tower state.
     * - You can use the Kotlin function [single] to either grab the single element from the list or throw an exception
     *   if there are 0 or more than one elements in the list. Note that we have already checked the outputs list has
     *   only one element in the previous task.
     * - We need to obtain a reference to the proposed Tower for issuance from the [LedgerTransaction.outputs] list.
     *   This list is typed as a list of [ContractState]s, therefore we need to cast the [ContractState] which we return
     *   from [single] to an [TowerRentalProposalState]. [BaseTransaction.outputsOfType] is a helper function that finds states
     *   of a specific type and casts the results to that type.
     *
     *       val state = tx.outputsOfType<X>().single()
     *
     * - When checking the [TowerRentalProposalState.amount] property is greater than zero, you need to check the
     *   [TowerRentalProposalState.amount.quantity] field.
     */
    @Test
    fun cannotCreateZeroValueTowers() {
        ledgerServices.ledger {
            transaction {
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.ProposeTowerRentalAgreement())
                output(TowerRentalContract.TOWER_CONTRACT_ID, TowerRentalProposalState(0.POUNDS, ALICE.party, BOB.party)) // Zero amount fails.
                this `fails with` "A newly issued Tower must have a positive amount."
            }
            transaction {
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.ProposeTowerRentalAgreement())
                output(TowerRentalContract.TOWER_CONTRACT_ID, TowerRentalProposalState(100.SWISS_FRANCS, ALICE.party, BOB.party))
                this.verifies()
            }
            transaction {
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.ProposeTowerRentalAgreement())
                output(TowerRentalContract.TOWER_CONTRACT_ID, TowerRentalProposalState(1.POUNDS, ALICE.party, BOB.party))
                this.verifies()
            }
            transaction {
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.ProposeTowerRentalAgreement())
                output(TowerRentalContract.TOWER_CONTRACT_ID, TowerRentalProposalState(10.DOLLARS, ALICE.party, BOB.party))
                this.verifies()
            }
        }
    }

    /**
     * Task 5.
     * For obvious reasons, the identity of the lender and borrower must be different.
     * TODO: Add a contract constraint to check the lender is not the borrower.
     * Hint:
     * - You can use the [TowerRentalProposalState.lender] and [TowerRentalProposalState.borrower] properties.
     * - This check must be made before the checking who has signed.
     */
    @Test
    fun lenderAndBorrowerCannotBeTheSame() {
        val towerState = TowerRentalProposalState(1.POUNDS, ALICE.party, BOB.party)
        val borrowerIsLenderIou = TowerRentalProposalState(10.POUNDS, ALICE.party, ALICE.party)
        ledgerServices.ledger {
            transaction {
                command(listOf(ALICE.publicKey, BOB.publicKey),TowerRentalContract.Commands.ProposeTowerRentalAgreement())
                output(TowerRentalContract.TOWER_CONTRACT_ID, borrowerIsLenderIou)
                this `fails with` "The lender and borrower cannot have the same identity."
            }
            transaction {
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.ProposeTowerRentalAgreement())
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerState)
                this.verifies()
            }
        }
    }

    /**
     * Task 6.
     * The list of public keys which the commands hold should contain all of the participants defined in the [TowerRentalProposalState].
     * This is because the Tower is a bilateral agreement where both parties involved are required to sign to issue an
     * Tower or change the properties of an existing Tower.
     * TODO: Add a contract constraint to check that all the required signers are [TowerRentalProposalState] participants.
     * Hint:
     * - In Kotlin you can perform a set equality check of two sets with the == operator.
     * - We need to check that the signers for the transaction are a subset of the participants list.
     * - We don't want any additional public keys not listed in the Towers participants list.
     * - You will need a reference to the Issue command to get access to the list of signers.
     * - [requireSingleCommand] returns the single required command - you can assign the return value to a constant.
     *
     * Kotlin Hints
     * Kotlin provides a map function for easy conversion of a [Collection] using map
     * - https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/map.html
     * [Collection] can be turned into a set using toSet()
     * - https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/to-set.html
     */
    @Test
    fun lenderAndBorrowerMustSignIssueTransaction() {
        val towerState = TowerRentalProposalState(1.POUNDS, ALICE.party, BOB.party)
        ledgerServices.ledger {
            transaction {
                command(DUMMY.publicKey, TowerRentalContract.Commands.ProposeTowerRentalAgreement())
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerState)
                this `fails with` "Both lender and borrower together only may sign Tower issue transaction."
            }
            transaction {
                command(ALICE.publicKey, TowerRentalContract.Commands.ProposeTowerRentalAgreement())
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerState)
                this `fails with` "Both lender and borrower together only may sign Tower issue transaction."
            }
            transaction {
                command(BOB.publicKey, TowerRentalContract.Commands.ProposeTowerRentalAgreement())
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerState)
                this `fails with` "Both lender and borrower together only may sign Tower issue transaction."
            }
            transaction {
                command(listOf(BOB.publicKey, BOB.publicKey, BOB.publicKey), TowerRentalContract.Commands.ProposeTowerRentalAgreement())
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerState)
                this `fails with` "Both lender and borrower together only may sign Tower issue transaction."
            }
            transaction {
                command(listOf(BOB.publicKey, BOB.publicKey, MINICORP.publicKey, ALICE.publicKey), TowerRentalContract.Commands.ProposeTowerRentalAgreement())
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerState)
                this `fails with` "Both lender and borrower together only may sign Tower issue transaction."
            }
            transaction {
                command(listOf(BOB.publicKey, BOB.publicKey, BOB.publicKey, ALICE.publicKey), TowerRentalContract.Commands.ProposeTowerRentalAgreement())
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerState)
                this.verifies()
            }
            transaction {
                command(listOf(ALICE.publicKey, BOB.publicKey),TowerRentalContract.Commands.ProposeTowerRentalAgreement())
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerState)
                this.verifies()
            }
        }
    }
}
