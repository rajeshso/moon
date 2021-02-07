package com.n2.moon.tower.flow

import com.n2.moon.tower.contracts.TowerRentalContract
import com.n2.moon.tower.flows.InitiateTowerRentalProposalFlow
import com.n2.moon.tower.flows.InitiateTowerRentalProposalFlowResponder
import com.n2.moon.tower.flows.ReRejectTowerRentalProposalFlowResponder
import com.n2.moon.tower.flows.RejectTowerRentalProposalFlow
import com.n2.moon.tower.states.TowerRentalProposalState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.withoutIssuer
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.packageName
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.utils.sumCash
import net.corda.finance.schemas.CashSchemaV1
import net.corda.samples.obligation.flows.SelfIssueCashFlow
import net.corda.testing.internal.chooseIdentityAndCert
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Practical exercise instructions Flows part 3.
 * Uncomment the unit tests and use the hints + unit test body to complete the FLows such that the unit tests pass.
 */
class RejectTowerRentalProposalFlowTests {
    lateinit var mockNetwork: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode
    lateinit var c: StartedMockNode

    @Before
    fun setup() {
        mockNetwork = MockNetwork(listOf("com.n2.moon.tower", "net.corda.finance.contracts.asset", CashSchemaV1::class.packageName),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB"))))
        a = mockNetwork.createNode(MockNodeParameters())
        b = mockNetwork.createNode(MockNodeParameters())
        c = mockNetwork.createNode(MockNodeParameters())
        val startedNodes = arrayListOf(a, b, c)
        // For real nodes this happens automatically, but we have to manually register the flow for tests
        startedNodes.forEach { it.registerInitiatedFlow(InitiateTowerRentalProposalFlowResponder::class.java) }
        startedNodes.forEach { it.registerInitiatedFlow(ReRejectTowerRentalProposalFlowResponder::class.java) }
        mockNetwork.runNetwork()
    }

    @After
    fun tearDown() {
        mockNetwork.stopNodes()
    }

    /**
     * Issue an Tower on the ledger, we need to do this before we can transfer one.
     */
    private fun installTower(towerRentalProposalState: TowerRentalProposalState): SignedTransaction {
        val flow = InitiateTowerRentalProposalFlow(towerRentalProposalState)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    /**
     * Issue some on-ledger cash to ourselves, we need to do this before we can Settle an Tower.
     */
    private fun issueCash(amount: Amount<Currency>): Cash.State {
        val flow = SelfIssueCashFlow(amount)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    /**
     * Task 1.
     * The first task is to grab the [TowerRentalProposalState] for the given [linearId] from the vault, assemble a transaction
     * and sign it.
     * TODO: Grab the Tower for the given [linearId] from the vault, build and sign the settle transaction.
     * Hints:
     * - Use the code from the [ProposeRentalAgreementFlow] to get the correct [TowerRentalProposalState] from the vault.
     * - You will need to use the [CashUtils.generateSpend] functionality of the vault to add the cash states and cash command
     *   to your transaction. The API is quite simple. It takes a reference to a [ServiceHub], the [TransactionBuilder],
     *   an [Amount], [PartyAndCertificate] representing out identity (sender) and the [Party] object for the recipient.
     *   The function will mutate your builder by adding the states and commands.
     * - You then need to produce the output [TowerRentalProposalState] by using the [TowerRentalProposalState.pay] function.
     * - Add the input [TowerRentalProposalState] [StateAndRef] and the new output [TowerRentalProposalState] to the transaction.
     * - Sign the transaction and return it.
     */
    @Test
    fun flowReturnsCorrectlyFormedPartiallySignedTransaction() {
        val stx = installTower(TowerRentalProposalState(10.POUNDS, b.info.chooseIdentityAndCert().party, a.info.chooseIdentityAndCert().party))
        issueCash(5.POUNDS)
        val inputTower = stx.tx.outputs.single().data as TowerRentalProposalState
        val flow = RejectTowerRentalProposalFlow(inputTower.linearId, 5.POUNDS)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        val settleResult = future.getOrThrow()
        // Check the transaction is well formed...
        // One output TowerState, one input TowerState reference, input and output cash
        a.transaction {
            val ledgerTx = settleResult.toLedgerTransaction(a.services, false)
            assert(ledgerTx.inputs.size == 2)
            assert(ledgerTx.outputs.size == 2)
            val outputTower = ledgerTx.outputs.map { it.data }.filterIsInstance<TowerRentalProposalState>().single()
            assertEquals(
                    outputTower,
                    inputTower.pay(5.POUNDS))
            // Sum all the output cash. This is complicated as there may be multiple cash output states with not all of them
            // being assigned to the lender.
            val outputCashSum = ledgerTx.outputs
                    .map { it.data }
                    .filterIsInstance<Cash.State>()
                    .filter { it.owner == b.info.chooseIdentityAndCert().party }
                    .sumCash()
                    .withoutIssuer()
            // Compare the cash assigned to the lender with the amount claimed is being settled by the borrower.
            assertEquals(
                    outputCashSum,
                    (inputTower.amount - inputTower.paid - outputTower.paid))
            val command = ledgerTx.commands.requireSingleCommand<TowerRentalContract.Commands>()
            assert(command.value == TowerRentalContract.Commands.RejectTowerRentalAgreement())
            // Check the transaction has been signed by the borrower.
            settleResult.verifySignaturesExcept(b.info.chooseIdentityAndCert().party.owningKey,
                    mockNetwork.defaultNotaryNode.info.legalIdentitiesAndCerts.first().owningKey)
        }
    }

    /**
     * Task 2.
     * Only the borrower should be running this flow for a particular Tower.
     * TODO: Grab the Tower for the given [linearId] from the vault and check the node running the flow is the borrower.
     * Hint: Use the data within the tower obtained from the vault to check the right node is running the flow.
     */
    @Test
    fun settleFlowCanOnlyBeRunByBorrower() {
        val stx = installTower(TowerRentalProposalState(10.POUNDS, b.info.chooseIdentityAndCert().party, a.info.chooseIdentityAndCert().party))
        issueCash(5.POUNDS)
        val inputTower = stx.tx.outputs.single().data as TowerRentalProposalState
        val flow = RejectTowerRentalProposalFlow(inputTower.linearId, 5.POUNDS)
        val future = b.startFlow(flow)
        mockNetwork.runNetwork()
        assertFailsWith<IllegalArgumentException> { future.getOrThrow() }
    }

    /**
     * Task 3.
     * The borrower must have at least SOME cash in the right currency to pay the lender.
     * TODO: Add a check in the flow to ensure that the borrower has a balance of cash in the right currency.
     * Hint:
     * - Use [serviceHub.getCashBalances] - it is a map which can be queried by [Currency].
     * - Use an if statement to check there is cash in the right currency present.
     */
    @Test
    fun borrowerMustHaveCashInRightCurrency() {
        val stx = installTower(TowerRentalProposalState(10.POUNDS, b.info.chooseIdentityAndCert().party, a.info.chooseIdentityAndCert().party))
        val inputTower = stx.tx.outputs.single().data as TowerRentalProposalState
        val flow = RejectTowerRentalProposalFlow(inputTower.linearId, 5.POUNDS)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        assertFailsWith<IllegalArgumentException>("Borrower has no GBP to settle.") { future.getOrThrow() }
    }

    /**
     * Task 4.
     * The borrower must have enough cash in the right currency to pay the lender.
     * TODO: Add a check in the flow to ensure that the borrower has enough cash to pay the lender.
     * Hint: Add another if statement similar to the one required above.
     */
    @Test
    fun borrowerMustHaveEnoughCashInRightCurrency() {
        val stx = installTower(TowerRentalProposalState(10.POUNDS, b.info.chooseIdentityAndCert().party, a.info.chooseIdentityAndCert().party))
        issueCash(1.POUNDS)
        val inputTower = stx.tx.outputs.single().data as TowerRentalProposalState
        val flow = RejectTowerRentalProposalFlow(inputTower.linearId, 5.POUNDS)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        assertFailsWith<IllegalArgumentException>("Borrower has only 1.00 GBP but needs 5.00 GBP to settle.") { future.getOrThrow() }
    }

    /**
     * Task 5.
     * We need to get the transaction signed by the other party.
     * TODO: Use a subFlow call to [initateFlow] and the [SignTransactionFlow] to get a signature from the lender.
     */
    @Test
    fun flowReturnsTransactionSignedByBothParties() {
        val stx = installTower(TowerRentalProposalState(10.POUNDS, b.info.chooseIdentityAndCert().party, a.info.chooseIdentityAndCert().party))
        issueCash(5.POUNDS)
        val inputTower = stx.tx.outputs.single().data as TowerRentalProposalState
        val flow = RejectTowerRentalProposalFlow(inputTower.linearId, 5.POUNDS)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        val settleResult = future.getOrThrow()
        // Check the transaction is well formed...
        // One output TowerState, one input TowerState reference, input and output cash
        settleResult.verifySignaturesExcept(mockNetwork.defaultNotaryNode.info.legalIdentitiesAndCerts.first().owningKey)
    }

    /**
     * Task 6.
     * We need to get the transaction signed by the notary service
     * TODO: Use a subFlow call to the [FinalityFlow] to get a signature from the lender.
     */
    @Test
    fun flowReturnsCommittedTransaction() {
        val stx = installTower(TowerRentalProposalState(10.POUNDS, b.info.chooseIdentityAndCert().party, a.info.chooseIdentityAndCert().party))
        issueCash(5.POUNDS)
        val inputTower = stx.tx.outputs.single().data as TowerRentalProposalState
        val flow = RejectTowerRentalProposalFlow(inputTower.linearId, 5.POUNDS)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        val settleResult = future.getOrThrow()
        // Check the transaction is well formed...
        // One output TowerState, one input TowerState reference, input and output cash
        settleResult.verifyRequiredSignatures()
    }
}
