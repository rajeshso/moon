package com.n2.moon.tower.states

import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.samples.obligation.ALICE
import net.corda.samples.obligation.BOB
import net.corda.samples.obligation.MEGACORP
import net.corda.samples.obligation.MINICORP
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * Practical exercise instructions.
 * Uncomment the first unit test [hasTowerAmountFieldOfCorrectType] then run the unit test using the green arrow
 * to the left of the [TowerRentalProposalStateTests] class or the [hasTowerAmountFieldOfCorrectType] method.
 * Running the unit tests from [TowerRentalProposalStateTests] runs all of the unit tests defined in the class.
 * The test should fail because you need to make some changes to the TowerState to make the test pass. Read the TODO
 * under each task number for a description and a hint of what you need to do.
 * Once you have the unit test passing, uncomment the next test.
 * Continue until all the unit tests pass.
 * Hint: CMD / Ctrl + click on the brown type names in square brackets for that type's definition in the codebase.
 */
class TowerRentalProposalStateTests {

    /**
     * Task 1.
     * TODO: Add an 'amount' property of type [Amount] to the [TowerRentalProposalState] class to get this test to pass.
     * Hint: [Amount] is a template class that takes a class parameter of the token you would like an [Amount] of.
     * As we are dealing with cash lent from one Party to another a sensible token to use would be [Currency].
     */
    @Test
    fun hasTowerAmountFieldOfCorrectType() {
        // Does the amount field exist?
        TowerRentalProposalState::class.java.getDeclaredField("rentalAmount")
        // Is the amount field of the correct type?
        assertEquals(TowerRentalProposalState::class.java.getDeclaredField("rentalAmount").type, Amount::class.java)
    }

    /**
     * Task 2.
     * TODO: Add a 'proposer' property of type [Party] to the [TowerRentalProposalState] class to get this test to pass.
     */
    @Test
    fun hasProposerFieldOfCorrectType() {
        // Does the proposer field exist?
        TowerRentalProposalState::class.java.getDeclaredField("proposerParty")
        // Is the proposer field of the correct type?
        assertEquals(TowerRentalProposalState::class.java.getDeclaredField("proposerParty").type, Party::class.java)
    }

    /**
     * Task 3.
     * TODO: Add a 'agreementParty' property of type [Party] to the [TowerRentalProposalState] class to get this test to pass.
     */
    @Test
    fun hasAgreementPartyFieldOfCorrectType() {
        // Does the agreementParty field exist?
        TowerRentalProposalState::class.java.getDeclaredField("agreementParty")
        // Is the agreementParty field of the correct type?
        assertEquals(TowerRentalProposalState::class.java.getDeclaredField("agreementParty").type, Party::class.java)
    }

    /**
     * Task 4.
     * TODO: Add a 'paid' property of type [Amount] to the [TowerRentalProposalState] class to get this test to pass.
     * Hint:
     * - We would like this property to be initialised to a zero amount of Currency upon creation of the [TowerRentalProposalState].
     * - You can use the [POUNDS] extension function over [Int] to create an amount of pounds e.g. '10.POUNDS'.
     * - This property keeps track of how much of the initial [TowerRentalProposalState.rentalAmount] has been settled by the agreementParty
     * - You can initialise a property with a default value in a Kotlin data class like this:
     *
     *       data class(val number: Int = 10)
     *
     * - We need to make sure that the [TowerRentalProposalState.paid] property is of the same currency type as the
     *   [TowerRentalProposalState.rentalAmount] property. You can create an instance of the [Amount] class that takes a zero value and a token
     *   representing the currency - which should be the same currency as the [TowerRentalProposalState.rentalAmount] property.
     */
    @Test
    fun hasPaidFieldOfCorrectType() {
        // Does the paid field exist?
        TowerRentalProposalState::class.java.getDeclaredField("paid")
        // Is the paid field of the correct type?
        assertEquals(TowerRentalProposalState::class.java.getDeclaredField("paid").type, Amount::class.java)
    }

    /**
     * Task 5.
     * TODO: Include the proposer within the [TowerRentalProposalState.participants] list
     * Hint: [listOf] takes any number of parameters and will add them to the list
     */
    @Test
    fun proposerIsParticipant() {
        val towerState = TowerRentalProposalState(1.POUNDS, ALICE.party, BOB.party)
        assertNotEquals(towerState.participants.indexOf(ALICE.party), -1)
    }

    /**
     * Task 6.
     * TODO: Similar to the last task, include the agreementParty within the [TowerRentalProposalState.participants] list
     */
    @Test
    fun agreementPartyIsParticipant() {
        val towerRentalProposalState = TowerRentalProposalState(1.POUNDS, ALICE.party, BOB.party)
        assertNotEquals(towerRentalProposalState.participants.indexOf(BOB.party), -1)
    }

    /**
     * Task 7.
     * TODO: Implement [LinearState] along with the required properties and methods.
     * Hint: [LinearState] implements [ContractState] which defines an additional property and method. You can use
     * IntellIJ to automatically add the member definitions for you or you can add them yourself. Look at the definition
     * of [LinearState] for what requires adding.
     */
    @Test
    fun isLinearState() {
        assert(LinearState::class.java.isAssignableFrom(TowerRentalProposalState::class.java))
    }

    /**
     * Task 8.
     * TODO: Override the [LinearState.linearId] property and assign it a value via your state's constructor.
     * Hint:
     * - The [LinearState.linearId] property is of type [UniqueIdentifier]. You need to create a new instance of
     * the [UniqueIdentifier] class.
     * - The [LinearState.linearId] is designed to link all [LinearState]s (which represent the state of an
     * agreement at a specific point in time) together. All the [LinearState]s with the same [LinearState.linearId]
     * represent the complete life-cycle to date of an agreement, asset or shared fact.
     * - Provide a default value for [linearId] for a new [TowerRentalProposalState]
     */
    @Test
    fun hasLinearIdFieldOfCorrectType() {
        // Does the linearId field exist?
        TowerRentalProposalState::class.java.getDeclaredField("linearId")
        // Is the linearId field of the correct type?
        assertEquals(TowerRentalProposalState::class.java.getDeclaredField("linearId").type, UniqueIdentifier::class.java)
        // Check field is set to a not null value
        val towerRentalProposalState = TowerRentalProposalState(1.POUNDS, ALICE.party, BOB.party)
        assertNotNull(towerRentalProposalState.linearId)
    }

    /**
     * Task 9.
     * TODO: Ensure parameters are ordered correctly.
     * Hint: Make sure that the proposer and agreementParty fields are not in the wrong order as this may cause some
     * confusion in subsequent tasks!
     */
    @Test
    fun checkTowerStateParameterOrdering() {
        val fields = TowerRentalProposalState::class.java.declaredFields
        val amountIdx = fields.indexOf(TowerRentalProposalState::class.java.getDeclaredField("rentalAmount"))
        val proposerIdx = fields.indexOf(TowerRentalProposalState::class.java.getDeclaredField("proposerParty"))
        val agreementPartyIdx = fields.indexOf(TowerRentalProposalState::class.java.getDeclaredField("agreementParty"))
        val paidIdx = fields.indexOf(TowerRentalProposalState::class.java.getDeclaredField("paid"))
        val linearIdIdx = fields.indexOf(TowerRentalProposalState::class.java.getDeclaredField("linearId"))

        assert(amountIdx < proposerIdx)
        assert(proposerIdx < agreementPartyIdx)
        assert(agreementPartyIdx < paidIdx)
        assert(paidIdx < linearIdIdx)
    }

    /**
     * Task 10.
     * TODO: Add a helper method called [pay] that can be called from an [TowerRentalProposalState] to settle an amount of the Tower.
     * Hint:
     * - You will need to increase the [TowerRentalProposalState.paid] property by the amount the agreementParty wishes to pay.
     * - Add a new function called [pay] in [TowerRentalProposalState]. This function will need to return an [TowerRentalProposalState].
     * - The existing state is immutable so a new state must be created from the existing state. Kotlin provides a [copy]
     * method which creates a new object with new values for specified fields.
     * - [copy] returns a copy of the object instance and the fields can be changed by specifying new values as
     * parameters to [copy]
     */
    @Test
    fun checkPayHelperMethod() {
        val towerRentalProposalState = TowerRentalProposalState(10.DOLLARS, ALICE.party, BOB.party)
        assertEquals(5.DOLLARS, towerRentalProposalState.pay(5.DOLLARS).paid)
        assertEquals(3.DOLLARS, towerRentalProposalState.pay(1.DOLLARS).pay(2.DOLLARS).paid)
        assertEquals(10.5.DOLLARS, towerRentalProposalState.pay(5.DOLLARS).pay(3.DOLLARS).pay(2.5.DOLLARS).paid)
    }

    /**
     * Task 11.
     * TODO: Add a helper method called [withNewProposer] that can be called from an [TowerRentalProposalState] to change the Tower's proposer.
     */
    @Test
    fun checkWithNewProposerHelperMethod() {
        val towerRentalProposalState = TowerRentalProposalState(10.DOLLARS, ALICE.party, BOB.party)
        assertEquals(MINICORP.party, towerRentalProposalState.withNewProposer(MINICORP.party).proposerParty)
        assertEquals(MEGACORP.party, towerRentalProposalState.withNewProposer(MINICORP.party).withNewProposer(MEGACORP.party).proposerParty)
    }
}
