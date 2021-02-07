package com.n2.moon.tower.flow

import com.n2.moon.tower.contracts.TowerRentalContract
import com.n2.moon.tower.flows.InitiateTowerRentalProposalFlow
import com.n2.moon.tower.flows.InitiateTowerRentalProposalFlowResponder
import com.n2.moon.tower.states.TowerRentalProposalState
import groovy.util.GroovyTestCase.assertEquals
import net.corda.core.contracts.Command
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.testing.internal.chooseIdentityAndCert
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * Practical exercise instructions Flows part 1.
 * Uncomment the unit tests and use the hints + unit test body to complete the Flows such that the unit tests pass.
 * Note! These tests rely on Quasar to be loaded, set your run configuration to "-ea -javaagent:lib/quasar.jar"
 * Run configuration can be edited in IntelliJ under Run -> Edit Configurations -> VM options
 * On some machines/configurations you may have to provide a full path to the quasar.jar file.
 * On some machines/configurations you may have to use the "JAR manifest" option for shortening the command line.
 */
class InitiateTowerRentalProposalFlowTests {
    lateinit var mockNetwork: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode

    @Before
    fun setup() {
        mockNetwork = MockNetwork(
            listOf("com.n2.moon.tower"),
            notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB")))
        )
        a = mockNetwork.createNode(MockNodeParameters())
        b = mockNetwork.createNode(MockNodeParameters())
        val startedNodes = arrayListOf(a, b)
        // For real nodes this happens automatically, but we have to manually register the flow for tests
        startedNodes.forEach { it.registerInitiatedFlow(InitiateTowerRentalProposalFlowResponder::class.java) }
        mockNetwork.runNetwork()
    }

    @After
    fun tearDown() {
        mockNetwork.stopNodes()
    }

    /**
     * Task 1.
     * Build out the [TowerInstallFlow]!
     * TODO: Implement the [TowerInstallFlow] flow which builds and returns a partially [SignedTransaction].
     * Hint:
     * - There's a lot to do to get this unit test to pass!
     * - Create a [TransactionBuilder] and pass it a notary reference.
     * -- A notary [Party] object can be obtained from [FlowLogic.serviceHub.networkMapCache].
     * -- In this training project there is only one notary
     * - Create an [TowerRentalContract.Commands.ProposeTowerRentalAgreement] inside a new [Command].
     * -- The required signers will be the same as the state's participants
     * -- Add the [Command] to the transaction builder [addCommand].
     * - Use the flow's [TowerRentalProposalState] parameter as the output state with [addOutputState]
     * - Extra credit: use [TransactionBuilder.withItems] to create the transaction instead
     * - Sign the transaction and convert it to a [SignedTransaction] using the [serviceHub.signInitialTransaction] method.
     * - Return the [SignedTransaction].
     */
    @Test
    fun flowReturnsCorrectlyFormedPartiallySignedTransaction() {
        val proposer = a.info.chooseIdentityAndCert().party
        val borrower = b.info.chooseIdentityAndCert().party
        val towerState = TowerRentalProposalState(10.POUNDS, proposer, borrower)
        val flow = InitiateTowerRentalProposalFlow(towerState)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        // Return the unsigned(!) SignedTransaction object from the InstallTowerFlow.
        val ptx: SignedTransaction = future.getOrThrow()
        // Print the transaction for debugging purposes.
        println(ptx.tx)
        // Check the transaction is well formed...
        // No outputs, one input TowerState and a command with the right properties.
        assert(ptx.tx.inputs.isEmpty())
        assert(ptx.tx.outputs.single().data is TowerRentalProposalState)
        val command = ptx.tx.commands.single()
        assert(command.value is TowerRentalContract.Commands.ProposeTowerRentalAgreement)
        assert(command.signers.toSet() == towerState.participants.map { it.owningKey }.toSet())
        ptx.verifySignaturesExcept(
            borrower.owningKey,
            mockNetwork.defaultNotaryNode.info.legalIdentitiesAndCerts.first().owningKey
        )
    }

    /**
     * Task 2.
     * Now we have a well formed transaction, we need to properly verify it using the [TowerRentalContract].
     * TODO: Amend the [InitiateTowerRentalProposalFlow] to verify the transaction as well as sign it.
     * Hint: You can verify on the builder directly prior to finalizing the transaction. This way
     * you can confirm the transaction prior to making it immutable with the signature.
     */
    @Test
    fun flowReturnsVerifiedPartiallySignedTransaction() {
        // Check that a zero amount TowerState fails.
        val proposer = a.info.chooseIdentityAndCert().party
        val borrower = b.info.chooseIdentityAndCert().party
        val zeroTower = TowerRentalProposalState(0.POUNDS, proposer, borrower)
        val futureOne = a.startFlow(InitiateTowerRentalProposalFlow(zeroTower))
        mockNetwork.runNetwork()
        assertFailsWith<TransactionVerificationException> { futureOne.getOrThrow() }
        // Check that an TowerState with the same participants fails.
        val borrowerIsProposerTower = TowerRentalProposalState(10.POUNDS, proposer, proposer)
        val futureTwo = a.startFlow(InitiateTowerRentalProposalFlow(borrowerIsProposerTower))
        mockNetwork.runNetwork()
        assertFailsWith<TransactionVerificationException> { futureTwo.getOrThrow() }
        // Check a good TowerState passes.
        val towerState = TowerRentalProposalState(10.POUNDS, proposer, borrower)
        val futureThree = a.startFlow(InitiateTowerRentalProposalFlow(towerState))
        mockNetwork.runNetwork()
        futureThree.getOrThrow()
    }

    /**
     * IMPORTANT: Review the [CollectSignaturesFlow] before continuing here.
     * Task 3.
     * Now we need to collect the signature from the [otherParty] using the [CollectSignaturesFlow].
     * TODO: Amend the [InitiateTowerRentalProposalFlow] to collect the [otherParty]'s signature.
     * Hint:
     * On the Initiator side:
     * - Get a set of signers required from the participants who are not the node
     * - - [ourIdentity] will give you the identity of the node you are operating as
     * - Use [initiateFlow] to get a set of [FlowSession] objects
     * - - Using [state.participants] as a base to determine the sessions needed is recommended. [participants] is on
     * - - the state interface so it is guaranteed to exist where [proposer] and [borrower] are not.
     * - - Hint: [ourIdentity] will give you the [Party] that represents the identity of the initiating flow.
     * - Use [subFlow] to start the [CollectSignaturesFlow]
     * - Pass it a [SignedTransaction] object and [FlowSession] set
     * - It will return a [SignedTransaction] with all the required signatures
     * - The subflow performs the signature checking and transaction verification for you
     *
     * On the Responder side:
     * - Create a subclass of [SignTransactionFlow]
     * - Override [SignTransactionFlow.checkTransaction] to impose any constraints on the transaction
     *
     * Using this flow you abstract away all the back-and-forth communication required for parties to sign a
     * transaction.
     */
    @Test
    fun flowReturnsTransactionSignedByBothParties() {
        val proposer = a.info.chooseIdentityAndCert().party
        val borrower = b.info.chooseIdentityAndCert().party
        val towerState = TowerRentalProposalState(10.POUNDS, proposer, borrower)
        val flow = InitiateTowerRentalProposalFlow(towerState)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        val stx = future.getOrThrow()
        stx.verifyRequiredSignatures()
    }

    /**
     * Task 4.
     * Now we need to store the finished [SignedTransaction] in both counter-party vaults.
     * TODO: Amend the [InitiateTowerRentalProposalFlow] by adding a call to [FinalityFlow].
     * Hint:
     * - As mentioned above, use the [FinalityFlow] to ensure the transaction is recorded in both [Party] vaults.
     * - Do not use the [BroadcastTransactionFlow]!
     * - The [FinalityFlow] determines if the transaction requires notarisation or not.
     * - We don't need the notary's signature as this is an issuance transaction without a timestamp. There are no
     *   inputs in the transaction that could be double spent! If we added a timestamp to this transaction then we
     *   would require the notary's signature as notaries act as a timestamping authority.
     */
    @Test
    fun flowRecordsTheSameTransactionInBothPartyVaults() {
        val proposer = a.info.chooseIdentityAndCert().party
        val borrower = b.info.chooseIdentityAndCert().party
        val towerState = TowerRentalProposalState(10.POUNDS, proposer, borrower)
        val flow = InitiateTowerRentalProposalFlow(towerState)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        val stx = future.getOrThrow()
        println("Signed transaction hash: ${stx.id}")
        listOf(a, b).map {
            it.services.validatedTransactions.getTransaction(stx.id)
        }.forEach {
            val txHash = (it as SignedTransaction).id
            println("$txHash == ${stx.id}")
            assertEquals(stx.id, txHash)
        }
    }
}
