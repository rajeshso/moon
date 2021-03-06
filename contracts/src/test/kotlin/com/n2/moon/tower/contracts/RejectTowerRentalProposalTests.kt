package com.n2.moon.tower.contracts

import com.n2.moon.tower.states.TowerRentalProposalState
import com.n2.moon.tower.states.TowerState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.withoutIssuer
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.packageName
import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.schemas.CashSchemaV1
import net.corda.samples.obligation.ALICE
import net.corda.samples.obligation.BOB
import net.corda.samples.obligation.CHARLIE
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test
import java.util.*

/**
 * Practical exercise instructions for Contracts Part 3.
 * The objective here is to write some contract code that verifies a transaction to settle an [TowerRentalProposalState].
 * Settling is more complicated than transferring and issuing as it requires you to use multiple state types in a
 * transaction.
 * As with the [IOUIssueTests] and [IOUTransferTests] uncomment each unit test and run them one at a time. Use the body
 * of the tests and the task description to determine how to get the tests to pass.
 */
class RejectTowerRentalProposalTests {
    var towerState = TowerState("some latitude","some longitude", "some height",
            "some spec",10, 10.POUNDS, ALICE.party, 0)
    private fun createCashState(amount: Amount<Currency>, owner: AbstractParty): Cash.State {
        val defaultRef = ByteArray(1) { 1 }
        return Cash.State(amount = amount `issued by`
                TestIdentity(CordaX500Name(organisation = "MegaCorp", locality = "MegaPlanet", country = "US")).ref(defaultRef.first()),
                owner = owner)
    }

    // A pre-defined dummy command.
    class DummyCommand : TypeOnlyCommandData()
    var ledgerServices = MockServices(listOf("com.n2.moon.tower.contracts","net.corda.samples.obligation.contract", "net.corda.finance.contracts.asset", CashSchemaV1::class.packageName))

    /**
     * Task 1.
     * We need to add another case to deal with settling in the [TowerRentalContract.verify] function.
     * TODO: Add the [TowerRentalContract.Commands.Settle] case to the verify function.
     * Hint: You can leave the body empty for now.
     */
    @Test
    fun mustIncludeSettleCommand() {
        val towerRentalProposalState = TowerRentalProposalState(10.POUNDS, ALICE.party, BOB.party, towerState = towerState)
        val inputCash = createCashState(5.POUNDS, BOB.party)
        val outputCash = inputCash.withNewOwner(newOwner = ALICE.party).ownableState
        ledgerServices.ledger {
            transaction {
                input(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState)
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState.pay(5.POUNDS))
                input(Cash.PROGRAM_ID, inputCash)
                output(Cash.PROGRAM_ID, outputCash)
                command(BOB.publicKey, Cash.Commands.Move())
                this.failsWith("Contract verification failed");
            }
            transaction {
                input(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState)
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState.pay(5.POUNDS))
                input(Cash.PROGRAM_ID, inputCash)
                output(Cash.PROGRAM_ID, outputCash)
                command(BOB.publicKey, Cash.Commands.Move())
                command(listOf(ALICE.publicKey, BOB.publicKey), DummyCommand()) // Wrong type.
                this.failsWith("Contract verification failed");
            }
            transaction {
                input(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState)
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState.pay(5.POUNDS))
                input(Cash.PROGRAM_ID, inputCash)
                output(Cash.PROGRAM_ID, outputCash)
                command(BOB.publicKey, Cash.Commands.Move())
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement()) // Correct Type.
                this.verifies()
            }
        }
    }

    /**
     * Task 2.
     * For now, we only want to settle one IOU at once. We can use the [TransactionForContract.groupStates] function
     * to group the IOUs by their [linearId] property. We want to make sure there is only one group of input and output
     * IOUs.
     * TODO: Using [groupStates] add a constraint that checks for one group of input/output IOUs.
     * Hint:
     * - The [single] function enforces a single element in a list or throws an exception. The test checks the
     *   thrown exception so you do not have to define the exception message specifically in the code.
     * - The [groupStates] function takes two type parameters: the type of the state you wish to group by and the type
     *   of the grouping key used, in this case as you need to use the [linearId] and it is a [UniqueIdentifier].
     * - The [groupStates] also takes a lambda function which selects a property of the state to decide the groups.
     * - In Kotlin if the last argument of a function is a lambda, you can call it like this:
     *
     *       fun functionWithLambda() { it.property }
     *
     *   This is exactly how map / filter are used in Kotlin.
     */
    @Test
    fun mustBeOneGroupOfIOUs() {
        val rentalOne = TowerRentalProposalState(10.POUNDS, ALICE.party, BOB.party, towerState = towerState)
        val rentalTwo = TowerRentalProposalState(5.POUNDS, ALICE.party, BOB.party, towerState = towerState)
        val inputCash = createCashState(5.POUNDS, BOB.party)
        val outputCash = inputCash.withNewOwner(newOwner = ALICE.party)
        ledgerServices.ledger {
            transaction {
                input(TowerRentalContract.TOWER_CONTRACT_ID, rentalOne)
                input(TowerRentalContract.TOWER_CONTRACT_ID, rentalTwo)
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement())
                output(TowerRentalContract.TOWER_CONTRACT_ID, rentalOne.pay(5.POUNDS))
                input(Cash.PROGRAM_ID, inputCash)
                output(Cash.PROGRAM_ID, outputCash.ownableState)
                command(BOB.publicKey, Cash.Commands.Move())
                this `fails with` "List has more than one element."
            }
            transaction {
                input(TowerRentalContract.TOWER_CONTRACT_ID, rentalOne)
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement())
                output(TowerRentalContract.TOWER_CONTRACT_ID, rentalOne.pay(5.POUNDS))
                input(Cash.PROGRAM_ID, inputCash)
                output(Cash.PROGRAM_ID, outputCash.ownableState)
                command(BOB.publicKey, Cash.Commands.Move())
                this.verifies()
            }
        }
    }

    /**
     * Task 3.
     * There always has to be one input IOU in a settle transaction but there might not be an output IOU.
     * TODO: Add a constraint to check there is always one input IOU.
     */
    @Test
    fun mustHaveOneInputIOU() {
        val towerRentalProposalState = TowerRentalProposalState(10.POUNDS, ALICE.party, BOB.party, towerState = towerState)
        val rentalOne = TowerRentalProposalState(10.POUNDS, ALICE.party, BOB.party, towerState = towerState)
        val tenPounds = createCashState(10.POUNDS, BOB.party)
        val fivePounds = createCashState(5.POUNDS, BOB.party)
        ledgerServices.ledger {
            transaction {
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement())
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState)
                this `fails with` "There must be one input Tower Rental Proposal."
            }
            transaction {
                input(TowerRentalContract.TOWER_CONTRACT_ID, rentalOne)
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement())
                output(TowerRentalContract.TOWER_CONTRACT_ID, rentalOne.pay(5.POUNDS))
                input(Cash.PROGRAM_ID, fivePounds)
                output(Cash.PROGRAM_ID, fivePounds.withNewOwner(newOwner = ALICE.party).ownableState)
                command(BOB.publicKey, Cash.Commands.Move())
                this.verifies()
            }
            transaction {
                input(TowerRentalContract.TOWER_CONTRACT_ID, rentalOne)
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement())
                input(Cash.PROGRAM_ID, tenPounds)
                output(Cash.PROGRAM_ID, tenPounds.withNewOwner(newOwner = ALICE.party).ownableState)
                command(BOB.publicKey, Cash.Commands.Move())
                this.verifies()
            }
        }
    }

    /**
     * Task 4.
     * Now we need to ensure that there are cash states present in the outputs list. The [TowerRentalContract] doesn't care
     * about input cash as the validity of the cash transaction will be checked by the [Cash] contract. We do however
     * need to count how much cash is being used to settle and update our [TowerRentalProposalState] accordingly.
     * TODO: Filter out the cash states from the list of outputs list and assign them to a constant.
     * Hint:
     * - Use the [outputsOfType] extension function to filter the transaction's outputs by type, in this case [Cash.State].
     */
    @Test
    fun mustBeCashOutputStatesPresent() {
        val towerRentalProposalState = TowerRentalProposalState(10.DOLLARS, ALICE.party, BOB.party, towerState = towerState)
        val cash = createCashState(5.DOLLARS, BOB.party)
        val cashPayment = cash.withNewOwner(newOwner = ALICE.party)
        ledgerServices.ledger {
            transaction {
                input(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState)
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState.pay(5.DOLLARS))
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement())
                this `fails with` "There must be output cash."
            }
            transaction {
                input(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState)
                input(Cash.PROGRAM_ID, cash)
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState.pay(5.DOLLARS))
                output(Cash.PROGRAM_ID, cashPayment.ownableState)
                command(BOB.publicKey, cashPayment.command)
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement())
                this.verifies()
            }
        }
    }

    /**
     * Task 5.
     * Not only do we need to check that [Cash] output states are present but we need to check that the payer is
     * correctly assigning the proposer as the new owner of these states.
     * TODO: Add a constraint to check that we are the new owner of the output cash.
     * Hint:
     * - Not all of the input cash may be assigned to the proposer. Some of it may be sent back to the payer as change.
     * - We need to use the [Cash.State.owner] property to check to see that it is the value of the proposer public key.
     * - Use [filter] to filter over the list of cash states to get the ones which are being assigned to the proposer.
     * - Once we have this filtered list, we can sum the cash being paid so we know how much is being settled.
     */
    @Test
    fun mustBeCashOutputStatesWithRecipientAsOwner() {
        val towerRentalProposalState = TowerRentalProposalState(10.POUNDS, ALICE.party, BOB.party, towerState = towerState)
        val cash = createCashState(5.POUNDS, BOB.party)
        val invalidCashPayment = cash.withNewOwner(newOwner = CHARLIE.party)
        val validCashPayment = cash.withNewOwner(newOwner = ALICE.party)
        ledgerServices.ledger {
            transaction {
                input(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState)
                input(Cash.PROGRAM_ID, cash)
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState.pay(5.POUNDS))
                output(Cash.PROGRAM_ID, "outputs cash", invalidCashPayment.ownableState)
                command(BOB.publicKey, invalidCashPayment.command)
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement())
                this `fails with` "Output cash must be paid to the proposer."
            }
            transaction {
                input(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState)
                input(Cash.PROGRAM_ID, cash)
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState.pay(5.POUNDS))
                output(Cash.PROGRAM_ID, validCashPayment.ownableState)
                command(BOB.publicKey, validCashPayment.command)
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement())
                this.verifies()
            }
        }
    }

    /**
     * Task 6.
     * Now we need to sum the cash that is being assigned to the proposer and compare this total against how much of the
     * IOU is left to pay.
     * TODO: Add a constraint that checks we cannot be paid more than the remaining IOU amount left to pay.
     * Hint:
     * - The remaining amount of the IOU is the amount less the paid property.
     * - To sum a list of [Cash.State]s use the [sumCash] function.
     * - The [sumCash] function returns an [Issued<Amount<Currency>>] type. We don't care about the issuer so we can
     *   apply [withoutIssuer] to unwrap the [Amount] from [Issuer].
     * - We can compare the amount left to be paid on the input IOU to the amount of cash being used,
     *   ensuring the amount being paid isn't too much.
     */
    @Test
    fun cashSettlementAmountMustBeLessThanRemainingIOUAmount() {
        val towerRentalProposalState = TowerRentalProposalState(10.DOLLARS, ALICE.party, BOB.party, towerState = towerState)
        val elevenDollars = createCashState(11.DOLLARS, BOB.party)
        val tenDollars = createCashState(10.DOLLARS, BOB.party)
        val fiveDollars = createCashState(5.DOLLARS, BOB.party)
        ledgerServices.ledger {
            transaction {
                input(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState)
                input(Cash.PROGRAM_ID, elevenDollars)
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState.pay(11.DOLLARS))
                output(Cash.PROGRAM_ID, elevenDollars.withNewOwner(newOwner = ALICE.party).ownableState)
                command(BOB.publicKey, elevenDollars.withNewOwner(newOwner = ALICE.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement())
                this `fails with` "The amount settled cannot be more than the amount outstanding."
            }
            transaction {
                input(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState)
                input(Cash.PROGRAM_ID, fiveDollars)
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState.pay(5.DOLLARS))
                output(Cash.PROGRAM_ID, fiveDollars.withNewOwner(newOwner = ALICE.party).ownableState)
                command(BOB.publicKey, fiveDollars.withNewOwner(newOwner = ALICE.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement())
                this.verifies()
            }
            transaction {
                input(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState)
                input(Cash.PROGRAM_ID, tenDollars)
                output(Cash.PROGRAM_ID, tenDollars.withNewOwner(newOwner = ALICE.party).ownableState)
                command(BOB.publicKey, tenDollars.withNewOwner(newOwner = ALICE.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement())
                this.verifies()
            }
        }
    }

    /**
     * Task 7.
     * Kotlin's type system should handle this for you but it goes without saying that we should only be able to settle
     * in the currency that the IOU in denominated in.
     * TODO: You shouldn't have anything to do here but here are some tests just to make sure!
     */
    @Test
    fun cashSettlementMustBeInTheCorrectCurrency() {
        val towerRentalProposalState = TowerRentalProposalState(10.DOLLARS, ALICE.party, BOB.party, towerState = towerState)
        val tenDollars = createCashState(10.DOLLARS, BOB.party)
        val tenPounds = createCashState(10.POUNDS, BOB.party)
        ledgerServices.ledger {
            transaction {
                input(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState)
                input(Cash.PROGRAM_ID, tenPounds)
                output(Cash.PROGRAM_ID, tenPounds.withNewOwner(newOwner = ALICE.party).ownableState)
                command(BOB.publicKey, tenPounds.withNewOwner(newOwner = ALICE.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement())
                this `fails with` "Token mismatch: GBP vs USD"
            }
            transaction {
                input(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState)
                input(Cash.PROGRAM_ID, tenDollars)
                output(Cash.PROGRAM_ID, tenDollars.withNewOwner(newOwner = ALICE.party).ownableState)
                command(BOB.publicKey, tenDollars.withNewOwner(newOwner = ALICE.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement())
                this.verifies()
            }
        }
    }

    /**
     * Task 8.
     * If we fully settle the IOU, then we are done and thus don't require one on ledgerServices.ledger anymore. However, if we only
     * partially settle the IOU, then we want to keep the IOU on ledger with an amended [paid] property.
     * TODO: Write a constraint that ensures the correct behaviour depending on the amount settled vs amount remaining.
     * Hint: You can use a simple if statement and compare the total amount paid vs amount left to settle.
     */
    @Test
    fun mustOnlyHaveOutputIOUIfNotFullySettling() {
        val rentalProposalState = TowerRentalProposalState(10.DOLLARS, ALICE.party, BOB.party, towerState = towerState)
        val tenDollars = createCashState(10.DOLLARS, BOB.party)
        val fiveDollars = createCashState(5.DOLLARS, BOB.party)
        ledgerServices.ledger {
            transaction {
                input(TowerRentalContract.TOWER_CONTRACT_ID, rentalProposalState)
                input(Cash.PROGRAM_ID, fiveDollars)
                output(Cash.PROGRAM_ID, fiveDollars.withNewOwner(newOwner = ALICE.party).ownableState)
                command(BOB.publicKey, fiveDollars.withNewOwner(newOwner = BOB.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement())
                this `fails with` "There must be one output Tower."
            }
            transaction {
                input(TowerRentalContract.TOWER_CONTRACT_ID, rentalProposalState)
                input(Cash.PROGRAM_ID, fiveDollars)
                output(Cash.PROGRAM_ID, fiveDollars.withNewOwner(newOwner = ALICE.party).ownableState)
                output(TowerRentalContract.TOWER_CONTRACT_ID, rentalProposalState.pay(5.DOLLARS))
                command(BOB.publicKey, fiveDollars.withNewOwner(newOwner = BOB.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement())
                verifies()
            }
            transaction {
                input(Cash.PROGRAM_ID, tenDollars)
                input(TowerRentalContract.TOWER_CONTRACT_ID, rentalProposalState)
                output(TowerRentalContract.TOWER_CONTRACT_ID, rentalProposalState.pay(10.DOLLARS))
                output(Cash.PROGRAM_ID, tenDollars.withNewOwner(newOwner = ALICE.party).ownableState)
                command(BOB.publicKey, tenDollars.withNewOwner(newOwner = BOB.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement())
                this `fails with` "There must be no output Tower as it has been fully settled."
            }
            transaction {
                input(Cash.PROGRAM_ID, tenDollars)
                input(TowerRentalContract.TOWER_CONTRACT_ID, rentalProposalState)
                output(Cash.PROGRAM_ID, tenDollars.withNewOwner(newOwner = ALICE.party).ownableState)
                command(BOB.publicKey, tenDollars.withNewOwner(newOwner = BOB.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement())
                verifies()
            }
        }
    }

    /**
     * Task 9.
     * We want to make sure that the only property of the IOU which changes when we settle, is the paid amount.
     * TODO: Write a constraint to check only the paid property of the [TowerRentalProposalState] changes when settling.
     */
    @Test
    fun onlyPaidPropertyMayChange() {
        val rentalProposalState = TowerRentalProposalState(10.DOLLARS, ALICE.party, BOB.party, towerState = towerState)
        val fiveDollars = createCashState(5.DOLLARS, BOB.party)
        ledgerServices.ledger {
            transaction {
                input(TowerRentalContract.TOWER_CONTRACT_ID, rentalProposalState)
                input(Cash.PROGRAM_ID, fiveDollars)
                output(Cash.PROGRAM_ID, fiveDollars.withNewOwner(newOwner = ALICE.party).ownableState)
                output(TowerRentalContract.TOWER_CONTRACT_ID, rentalProposalState.copy(agreementParty = ALICE.party, paid = 5.DOLLARS))
                command(BOB.publicKey, fiveDollars.withNewOwner(newOwner = BOB.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement())
                this `fails with` "Only the paid amount can change."
            }
            transaction {
                input(TowerRentalContract.TOWER_CONTRACT_ID, rentalProposalState)
                input(Cash.PROGRAM_ID, fiveDollars)
                output(Cash.PROGRAM_ID, fiveDollars.withNewOwner(newOwner = ALICE.party).ownableState)
                output(TowerRentalContract.TOWER_CONTRACT_ID, rentalProposalState.pay(5.DOLLARS))
                command(BOB.publicKey, fiveDollars.withNewOwner(newOwner = BOB.party).command)
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement())
                verifies()
            }
        }
    }

    /**
     * Task 10.
     * Both the proposer and the agreementParty must have signed an IOU issue transaction.
     * TODO: Add a constraint to the contract code that ensures this is the case.
     */
    @Test
    fun mustBeSignedByAllParticipants() {
        val towerRentalProposalState = TowerRentalProposalState(10.DOLLARS, ALICE.party, BOB.party, towerState = towerState)
        val cash = createCashState(5.DOLLARS, BOB.party)
        val cashPayment = cash.withNewOwner(newOwner = ALICE.party)
        ledgerServices.ledger {
            transaction {
                input(Cash.PROGRAM_ID, cash)
                input(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState)
                output(Cash.PROGRAM_ID, cashPayment.ownableState)
                command(BOB.publicKey, cashPayment.command)
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState.pay(5.DOLLARS))
                command(listOf(ALICE.publicKey, CHARLIE.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement())
                failsWith("Both proposer and agreementParty together only must sign the Tower settle transaction.")
            }
            transaction {
                input(Cash.PROGRAM_ID, cash)
                input(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState)
                output(Cash.PROGRAM_ID, cashPayment.ownableState)
                command(BOB.publicKey, cashPayment.command)
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState.pay(5.DOLLARS))
                command(BOB.publicKey, TowerRentalContract.Commands.RejectTowerRentalAgreement())
                failsWith("Both proposer and agreementParty together only must sign the Tower settle transaction.")
            }
            transaction {
                input(Cash.PROGRAM_ID, cash)
                input(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState)
                output(Cash.PROGRAM_ID, cashPayment.ownableState)
                command(BOB.publicKey, cashPayment.command)
                output(TowerRentalContract.TOWER_CONTRACT_ID, towerRentalProposalState.pay(5.DOLLARS))
                command(listOf(ALICE.publicKey, BOB.publicKey), TowerRentalContract.Commands.RejectTowerRentalAgreement())
                verifies()
            }
        }
    }
}

