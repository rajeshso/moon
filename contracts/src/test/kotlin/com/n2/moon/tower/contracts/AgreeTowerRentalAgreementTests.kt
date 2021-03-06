package com.n2.moon.tower.contracts

import com.n2.moon.tower.states.TowerRentalProposalState
import com.n2.moon.tower.states.TowerState
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.internal.packageName
import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.finance.schemas.CashSchemaV1
import net.corda.samples.obligation.ALICE
import net.corda.samples.obligation.BOB
import net.corda.samples.obligation.CHARLIE
import net.corda.samples.obligation.MINICORP
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

/**
 * Practical exercise instructions for Contracts Part 2.
 * The objective here is to write some contract code that verifies a transaction to issue an [TowerRentalProposalState].
 * As with the [InitiateTowerRentalProposalFlow] uncomment each unit test and run them one at a time. Use the body of the tests and the
 * task description to determine how to get the tests to pass.
 */
class  AgreeTowerRentalAgreementTests {
    // A pre-made dummy state we may need for some of the tests.
    class DummyState : ContractState {
        override val participants: List<AbstractParty> get() = listOf()
    }
    var towerState = TowerState("some latitude","some longitude", "some height",
    "some spec",10, 10.POUNDS, ALICE.party, 0)
    // A dummy command.
    class DummyCommand : CommandData
    var ledgerServices = MockServices(listOf("net.corda.finance.contracts.asset", "com.n2.moon.tower.contracts",CashSchemaV1::class.packageName))

    /**
     * Task 1.
     * Now things are going to get interesting!
     * We need the [TowerRentalContract] to not only handle Issues of IOUs but now also Transfers.
     * Of course, we'll need to add a new Command and add some additional contract code to handle Transfers.
     * TODO: Add a "Transfer" command to the IOUState and update the verify() function to handle multiple commands.
     * Hint:
     * - As with the [Issue] command, add the [AgreeTowerRentalAgreement] command within the [TowerRentalContract.Commands].
     * - Again, we only care about the existence of the [AgreeTowerRentalAgreement] command in a transaction, therefore it should
     *   subclass the [TypeOnlyCommandData].
     * - You can use the [requireSingleCommand] function to check for the existence of a command which implements a
     *   specified interface. Instead of using
     *
     *       tx.commands.requireSingleCommand<Commands.Issue>()
     *
     *   You can instead use:
     *
     *       tx.commands.requireSingleCommand<Commands>()
     *
     *   To match any command that implements [TowerRentalContract.Commands]
     * - We then need to switch on the type of [Command.value], in Kotlin you can do this using a "when" block
     * - For each "when" block case, you can check the type of [Command.value] using the "is" keyword:
     *
     *       val command = ...
     *       when (command.value) {
     *           is Commands.X -> doSomething()
     *           is Commands.Y -> doSomethingElse()
     *       }
     * - The [requireSingleCommand] function will handle unrecognised types for you (see first unit test).
     */
    @Test
    fun mustHandleMultipleCommandValues() {
        val towerRentalProposalState = TowerRentalProposalState(10.POUNDS, ALICE.party, BOB.party, towerState = towerState)
        ledgerServices.ledger {
            transaction {
                output(TowerRentalContract::class.java.name, towerRentalProposalState)
                command(listOf(ALICE.publicKey, BOB.publicKey), DummyCommand())
                this `fails with` "Required com.n2.moon.tower.contracts.TowerRentalContract.Commands command"
            }
            transaction {
                output(TowerRentalContract::class.java.name, towerRentalProposalState)
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.ProposeTowerRentalAgreement())
                this.verifies()
            }
            transaction {
                input(TowerRentalContract::class.java.name, towerRentalProposalState)
                output(TowerRentalContract::class.java.name, towerRentalProposalState.withNewProposer(CHARLIE.party))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), TowerRentalContract.Commands.AgreeTowerRentalAgreement())
                this.verifies()
            }
        }
    }

    /**
     * Task 2.
     * The AgreeTowerRentalAgreement transaction should only have one input state and one output state.
     * TODO: Add constraints to the contract code to ensure a AgreeTowerRentalAgreement transaction has only one input and output state.
     * Hint:
     * - Look at the contract code for "Issue".
     */
    @Test
    fun mustHaveOneInputAndOneOutput() {
        val towerRentalProposalState = TowerRentalProposalState(10.POUNDS, ALICE.party, BOB.party, towerState = towerState)
        ledgerServices.ledger {
            transaction {
                input(TowerRentalContract::class.java.name, towerRentalProposalState)
                input(TowerRentalContract::class.java.name, DummyState())
                output(TowerRentalContract::class.java.name, towerRentalProposalState.withNewProposer(CHARLIE.party))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), TowerRentalContract.Commands.AgreeTowerRentalAgreement())
                this `fails with` "An agreement to a tower rental proposal transaction should only consume one input state."
            }
            transaction {
                output(TowerRentalContract::class.java.name, towerRentalProposalState)
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), TowerRentalContract.Commands.AgreeTowerRentalAgreement())
                this `fails with` "An agreement to a tower rental proposal transaction should only consume one input state."
            }
            transaction {
                input(TowerRentalContract::class.java.name, towerRentalProposalState)
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), TowerRentalContract.Commands.AgreeTowerRentalAgreement())
                this `fails with` "An agreement to a Tower rental proposal transaction should only create one output state."
            }
            transaction {
                input(TowerRentalContract::class.java.name, towerRentalProposalState)
                output(TowerRentalContract::class.java.name, towerRentalProposalState.withNewProposer(CHARLIE.party))
                output(TowerRentalContract::class.java.name, DummyState())
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), TowerRentalContract.Commands.AgreeTowerRentalAgreement())
                this `fails with` "An agreement to a Tower rental proposal transaction should only create one output state."
            }
            transaction {
                input(TowerRentalContract::class.java.name, towerRentalProposalState)
                output(TowerRentalContract::class.java.name, towerRentalProposalState.withNewProposer(CHARLIE.party))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), TowerRentalContract.Commands.AgreeTowerRentalAgreement())
                this.verifies()
            }
        }
    }

    /**
     * Task 3.
     * TODO: Add a constraint to the contract code to ensure only the proposer property can change when transferring IOUs.
     * Hint:
     * - You can use the [TowerRentalProposalState.copy] method.
     * - You can compare a copy of the input to the output with the proposer of the output as the proposer of the input.
     * - You'll need references to the input and output ious.
     * - Remember you need to cast the [ContractState]s to [TowerRentalProposalState]s.
     * - It's easier to take this approach then check all properties other than the proposer haven't changed, including
     *   the [linearId] and the [contract]!
     */
    @Test
    fun onlyTheProposerMayChange() {
        val towerRentalProposalState = TowerRentalProposalState(10.POUNDS, ALICE.party, BOB.party, towerState = towerState)
        ledgerServices.ledger {
            transaction {
                input(TowerRentalContract::class.java.name, TowerRentalProposalState(10.DOLLARS, ALICE.party, BOB.party, towerState = towerState))
                output(TowerRentalContract::class.java.name, TowerRentalProposalState(1.DOLLARS, ALICE.party, BOB.party, towerState = towerState))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), TowerRentalContract.Commands.AgreeTowerRentalAgreement())
                this `fails with` "Only the proposer property may change."
            }
            transaction {
                input(TowerRentalContract::class.java.name, TowerRentalProposalState(10.DOLLARS, ALICE.party, BOB.party, towerState = towerState))
                output(TowerRentalContract::class.java.name, TowerRentalProposalState(10.DOLLARS, ALICE.party, CHARLIE.party, towerState = towerState))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), TowerRentalContract.Commands.AgreeTowerRentalAgreement())
                this `fails with` "Only the proposer property may change."
            }
            transaction {
                input(TowerRentalContract::class.java.name, TowerRentalProposalState(10.DOLLARS, ALICE.party, BOB.party, 5.DOLLARS, towerState = towerState))
                output(TowerRentalContract::class.java.name, TowerRentalProposalState(10.DOLLARS, ALICE.party, BOB.party, 10.DOLLARS, towerState = towerState))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), TowerRentalContract.Commands.AgreeTowerRentalAgreement())
                this `fails with` "Only the proposer property may change."
            }
            transaction {
                input(TowerRentalContract::class.java.name, towerRentalProposalState)
                output(TowerRentalContract::class.java.name, towerRentalProposalState.withNewProposer(CHARLIE.party))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), TowerRentalContract.Commands.AgreeTowerRentalAgreement())
                this.verifies()
            }
        }
    }

    /**
     * Task 4.
     * It is fairly obvious that in a transfer IOU transaction the proposer must change.
     * TODO: Add a constraint to check the proposer has changed in the output IOU.
     */
    @Test
    fun theProposerMustChange() {
        val towerRentalProposalState = TowerRentalProposalState(10.POUNDS, ALICE.party, BOB.party, towerState = towerState)
        ledgerServices.ledger {
            transaction {
                input(TowerRentalContract::class.java.name, towerRentalProposalState)
                output(TowerRentalContract::class.java.name, towerRentalProposalState)
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), TowerRentalContract.Commands.AgreeTowerRentalAgreement())
                this `fails with` "The proposer property must change in a transfer."
            }
            transaction {
                input(TowerRentalContract::class.java.name, towerRentalProposalState)
                output(TowerRentalContract::class.java.name, towerRentalProposalState.withNewProposer(CHARLIE.party))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), TowerRentalContract.Commands.AgreeTowerRentalAgreement())
                this.verifies()
            }
        }
    }

    /**
     * Task 5.
     * All the participants in a transfer IOU transaction must sign.
     * TODO: Add a constraint to check the old proposer, the new proposer and the recipient have signed.
     */
    @Test
    fun allParticipantsMustSign() {
        val towerRentalProposalState = TowerRentalProposalState(10.POUNDS, ALICE.party, BOB.party, towerState= towerState)
        ledgerServices.ledger {
            transaction {
                input(TowerRentalContract::class.java.name, towerRentalProposalState)
                output(TowerRentalContract::class.java.name, towerRentalProposalState.withNewProposer(CHARLIE.party))
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.AgreeTowerRentalAgreement())
                this `fails with` "The agreementParty, old proposer and new proposer only must sign an Tower transfer transaction"
            }
            transaction {
                input(TowerRentalContract::class.java.name, towerRentalProposalState)
                output(TowerRentalContract::class.java.name, towerRentalProposalState.withNewProposer(CHARLIE.party))
                command(listOf(ALICE.publicKey, CHARLIE.publicKey), TowerRentalContract.Commands.AgreeTowerRentalAgreement())
                this `fails with` "The agreementParty, old proposer and new proposer only must sign an Tower transfer transaction"
            }
            transaction {
                input(TowerRentalContract::class.java.name, towerRentalProposalState)
                output(TowerRentalContract::class.java.name, towerRentalProposalState.withNewProposer(CHARLIE.party))
                command(listOf(BOB.publicKey, CHARLIE.publicKey), TowerRentalContract.Commands.AgreeTowerRentalAgreement())
                this `fails with` "The agreementParty, old proposer and new proposer only must sign an Tower transfer transaction"
            }
            transaction {
                input(TowerRentalContract::class.java.name, towerRentalProposalState)
                output(TowerRentalContract::class.java.name, towerRentalProposalState.withNewProposer(CHARLIE.party))
                command(listOf(ALICE.publicKey, BOB.publicKey, MINICORP.publicKey), TowerRentalContract.Commands.AgreeTowerRentalAgreement())
                this `fails with` "The agreementParty, old proposer and new proposer only must sign an Tower transfer transaction"
            }
            transaction {
                input(TowerRentalContract::class.java.name, towerRentalProposalState)
                output(TowerRentalContract::class.java.name, towerRentalProposalState.withNewProposer(CHARLIE.party))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey, MINICORP.publicKey), TowerRentalContract.Commands.AgreeTowerRentalAgreement())
                this `fails with` "The agreementParty, old proposer and new proposer only must sign an Tower transfer transaction"
            }
            transaction {
                input(TowerRentalContract::class.java.name, towerRentalProposalState)
                output(TowerRentalContract::class.java.name, towerRentalProposalState.withNewProposer(CHARLIE.party))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), TowerRentalContract.Commands.AgreeTowerRentalAgreement())
                this.verifies()
            }
        }
    }
}