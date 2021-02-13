package com.n2.moon.tower.flow

import com.n2.moon.tower.contracts.TowerRentalContract
import com.n2.moon.tower.flows.AgreeTowerRentalProposalFlow
import com.n2.moon.tower.flows.AgreeTowerRentalProposalFlowResponder
import com.n2.moon.tower.flows.InitiateTowerRentalProposalFlow
import com.n2.moon.tower.flows.InitiateTowerRentalProposalFlowResponder
import com.n2.moon.tower.states.TowerRentalProposalState
import com.n2.moon.tower.states.TowerState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.samples.obligation.ALICE
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
 * Practical exercise instructions Flows part 2.
 * Uncomment the unit tests and use the hints + unit test body to complete the Flows such that the unit tests pass.
 */
class AgreeTowerRentalProposalFlowTests {
    var towerState = TowerState("some latitude","some longitude", "some height",
            "some spec",10, 10.POUNDS, ALICE.party, 0)
    lateinit var mockNetwork: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode
    lateinit var c: StartedMockNode

    @Before
    fun setup() {
        mockNetwork = MockNetwork(listOf("com.n2.moon.tower"),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB"))))
        a = mockNetwork.createNode(MockNodeParameters())
        b = mockNetwork.createNode(MockNodeParameters())
        c = mockNetwork.createNode(MockNodeParameters())
        val startedNodes = arrayListOf(a, b, c)
        // For real nodes this happens automatically, but we have to manually register the flow for tests
        startedNodes.forEach { it.registerInitiatedFlow(InitiateTowerRentalProposalFlowResponder::class.java) }
        startedNodes.forEach { it.registerInitiatedFlow(AgreeTowerRentalProposalFlowResponder::class.java) }
        mockNetwork.runNetwork()
    }

    @After
    fun tearDown() {
        mockNetwork.stopNodes()
    }

    /**
     * Issue an Tower on the ledger, we need to do this before we can transfer one.
     */
    private fun installTowerRentalProposalState(towerRentalProposalState: TowerRentalProposalState): SignedTransaction {
        val flow = InitiateTowerRentalProposalFlow(towerRentalProposalState)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    /**
     * Task 1.
     * Build out the beginnings of [ProposeTowerRentalAgreement]!
     * TODO: Implement the [ProposeTowerRentalAgreement] flow which builds and returns a partially [SignedTransaction].
     * Hint:
     * - This flow will look similar to the [IntallTowerFlow].
     * - This time our transaction has an input state, so we need to retrieve it from the vault!
     * - You can use the [serviceHub.vaultService.queryBy] method to get the latest linear states of a particular
     *   type from the vault. It returns a list of states matching your query.
     * - Use the [UniqueIdentifier] which is passed into the flow to retrieve the correct [TowerRentalProposalState].
     * - Use the [TowerRentalProposalState.withNewProposer] method to create a copy of the state with a new proposer.
     * - Create a Command - we will need to use the Transfer command.
     * - Remember, as we are involving three parties we will need to collect three signatures, so need to add three
     *   [PublicKey]s to the Command's signers list. We can get the signers from the input Tower and the new Tower you
     *   have just created with the new proposer.
     * - Verify and sign the transaction as you did with the [IntallTowerFlow].
     * - Return the partially signed transaction.
     */
    @Test
    fun flowReturnsCorrectlyFormedPartiallySignedTransaction() {
        val proposer = a.info.chooseIdentityAndCert().party
        val agreementParty = b.info.chooseIdentityAndCert().party
        val stx = installTowerRentalProposalState(TowerRentalProposalState(10.POUNDS, proposer, agreementParty))
        val inputTower = stx.tx.outputs.single().data as TowerRentalProposalState
        val flow = AgreeTowerRentalProposalFlow(inputTower.linearId, c.info.chooseIdentityAndCert().party)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        val ptx = future.getOrThrow()
        // Check the transaction is well formed...
        // One output TowerState, one input state reference and a Transfer command with the right properties.
        assert(ptx.tx.inputs.size == 1)
        assert(ptx.tx.outputs.size == 1)
        assert(ptx.tx.inputs.single() == StateRef(stx.id, 0))
        println("Input state ref: ${ptx.tx.inputs.single()} == ${StateRef(stx.id, 0)}")
        val outputTower = ptx.tx.outputs.single().data as TowerRentalProposalState
        println("Output state: $outputTower")
        val command = ptx.tx.commands.single()
        assert(command.value == TowerRentalContract.Commands.AgreeTowerRentalAgreement())
        ptx.verifySignaturesExcept(b.info.chooseIdentityAndCert().party.owningKey, c.info.chooseIdentityAndCert().party.owningKey,
                mockNetwork.defaultNotaryNode.info.legalIdentitiesAndCerts.first().owningKey)
    }

    /**
     * Task 2.
     * We need to make sure that only the current proposer can execute this flow.
     * TODO: Amend the [ProposeTowerRentalAgreement] to only allow the current proposer to execute the flow.
     * Hint:
     * - Remember: You can use the node's identity and compare it to the [Party] object within the [TowerRentalProposalState] you
     *   retrieved from the vault.
     * - Throw an [IllegalArgumentException] if the wrong party attempts to run the flow!
     */
    @Test
    fun flowCanOnlyBeRunByCurrentProposer() {
        val proposer = a.info.chooseIdentityAndCert().party
        val agreementParty = b.info.chooseIdentityAndCert().party
        val stx = installTowerRentalProposalState(TowerRentalProposalState(10.POUNDS, proposer, agreementParty))
        val inputTower = stx.tx.outputs.single().data as TowerRentalProposalState
        val flow = AgreeTowerRentalProposalFlow(inputTower.linearId, c.info.chooseIdentityAndCert().party)
        val future = b.startFlow(flow)
        mockNetwork.runNetwork()
        assertFailsWith<IllegalArgumentException> { future.getOrThrow() }
    }

    /**
     * Task 3.
     * Check that an [TowerRentalProposalState] cannot be transferred to the same proposer.
     * TODO: You shouldn't have to do anything additional to get this test to pass. Belts and Braces!
     */
    @Test
    fun towerCannotBeTransferredToSameParty() {
        val proposer = a.info.chooseIdentityAndCert().party
        val agreementParty = b.info.chooseIdentityAndCert().party
        val stx = installTowerRentalProposalState(TowerRentalProposalState(10.POUNDS, proposer, agreementParty))
        val inputTower = stx.tx.outputs.single().data as TowerRentalProposalState
        val flow = AgreeTowerRentalProposalFlow(inputTower.linearId, proposer)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        // Check that we can't transfer an Tower to ourselves.
        assertFailsWith<TransactionVerificationException> { future.getOrThrow() }
    }

    /**
     * Task 4.
     * Get the agreementPartys and the new proposers signatures.
     * TODO: Amend the [ProposeTowerRentalAgreement] to handle collecting signatures from multiple parties.
     * Hint: use [initiateFlow] and the [CollectSignaturesFlow] in the same way you did for the [IntallTowerFlow].
     */
    @Test
    fun flowReturnsTransactionSignedByAllParties() {
        val proposer = a.info.chooseIdentityAndCert().party
        val agreementParty = b.info.chooseIdentityAndCert().party
        val stx = installTowerRentalProposalState(TowerRentalProposalState(10.POUNDS, proposer, agreementParty))
        val inputTower = stx.tx.outputs.single().data as TowerRentalProposalState
        val flow = AgreeTowerRentalProposalFlow(inputTower.linearId, c.info.chooseIdentityAndCert().party)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        future.getOrThrow().verifySignaturesExcept(mockNetwork.defaultNotaryNode.info.legalIdentitiesAndCerts.first().owningKey)
    }

    /**
     * Task 5.
     * We need to get the transaction signed by the notary service
     * TODO: Use a subFlow call to the [FinalityFlow] to get a signature from the proposer.
     */
    @Test
    fun flowReturnsTransactionSignedByAllPartiesAndNotary() {
        val proposer = a.info.chooseIdentityAndCert().party
        val agreementParty = b.info.chooseIdentityAndCert().party
        val stx = installTowerRentalProposalState(TowerRentalProposalState(10.POUNDS, proposer, agreementParty))
        val inputTower = stx.tx.outputs.single().data as TowerRentalProposalState
        val flow = AgreeTowerRentalProposalFlow(inputTower.linearId, c.info.chooseIdentityAndCert().party)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        future.getOrThrow().verifyRequiredSignatures()
    }
}
