package com.n2.moon.tower.flows


import co.paralleluniverse.fibers.Suspendable
import com.n2.moon.tower.contracts.TowerRentalContract
import com.n2.moon.tower.states.TowerRentalProposalState
import com.n2.moon.tower.states.TowerState
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.workflows.asset.CashUtils
import net.corda.finance.workflows.getCashBalance
import java.util.*

/**
 * This is the flow which handles the (partial) settlement of existing Towers on the ledger.
 * Gathering the counterparty's signature is handled by the [CollectSignaturesFlow].
 * Notarisation (if required) and commitment to the ledger is handled vy the [FinalityFlow].
 * The flow returns the [SignedTransaction] that was committed to the ledger.
 */
@InitiatingFlow
@StartableByRPC
class RejectTowerRentalProposalFlow(val linearId: UniqueIdentifier, val amount: Amount<Currency>): FlowLogic<SignedTransaction>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        // Step 1. Retrieve the Tower state from the vault.
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
        val iouToSettle = serviceHub.vaultService.queryBy<TowerRentalProposalState>(queryCriteria).states.single()
        val counterparty = iouToSettle.state.data.proposerParty

        // Step 2. Check the party running this flow is the agreementParty.
        if (ourIdentity != iouToSettle.state.data.agreementParty) {
            throw IllegalArgumentException("Tower settlement flow must be initiated by the agreementParty.")
        }

        // Step 3. Create a transaction builder.
        val notary = iouToSettle.state.notary
        val builder = TransactionBuilder(notary = notary)

        // Step 4. Check we have enough cash to settle the requested amount.
        val cashBalance = serviceHub.getCashBalance(amount.token)

        if (cashBalance < amount) {
            throw IllegalArgumentException("AgreementParty has only $cashBalance but needs $amount to settle.")
        } else if (amount > (iouToSettle.state.data.rentalAmount - iouToSettle.state.data.paid)) {
            throw IllegalArgumentException("AgreementParty tried to settle with $amount but only needs ${ (iouToSettle.state.data.rentalAmount - iouToSettle.state.data.paid) }")
        }

        // Step 5. Get some cash from the vault and add a spend to our transaction builder.
        // Vault might contain states "owned" by anonymous parties. This is one of techniques to anonymize transactions
        // generateSpend returns all public keys which have to be used to sign transaction
        val (_, cashKeys) = CashUtils.generateSpend(serviceHub, builder, amount, ourIdentityAndCert, counterparty)

        // Step 6. Add the Tower input state and settle command to the transaction builder.
        val settleCommand = Command(TowerRentalContract.Commands.RejectTowerRentalAgreement(), listOf(counterparty.owningKey, ourIdentity.owningKey))
        // Add the input Tower and Tower settle command.
        builder.addCommand(settleCommand)
        builder.addInputState(iouToSettle)

        // Step 7. Only add an output Tower state of the Tower has not been fully settled.
        val amountRemaining = iouToSettle.state.data.rentalAmount - iouToSettle.state.data.paid - amount
        if (amountRemaining > Amount(0, amount.token)) {
            val settledTower: TowerRentalProposalState = iouToSettle.state.data.pay(amount)
            builder.addOutputState(settledTower, TowerRentalContract.TOWER_CONTRACT_ID)
        }

        // Step 8. Verify and sign the transaction.
        builder.verify(serviceHub)
        // We need to sign transaction with all keys referred from Cash input states + our public key
        val myKeysToSign = (cashKeys.toSet() + ourIdentity.owningKey).toList()
        val ptx = serviceHub.signInitialTransaction(builder, myKeysToSign)

        // Initialising session with other party
        val counterpartySession = initiateFlow(counterparty)

        // Sending other party our identities so they are aware of anonymous public keys
        subFlow(IdentitySyncFlow.Send(counterpartySession, ptx.tx))

        // Step 9. Collecting missing signatures
        val stx = subFlow(CollectSignaturesFlow(ptx, listOf(counterpartySession), myOptionalKeys = myKeysToSign))

        // Step 10. Finalize the transaction.
        return subFlow(FinalityFlow(stx, counterpartySession))
    }
}

/**
 * This is the flow which signs Tower settlements.
 * The signing is handled by the [SignTransactionFlow].
 */
@InitiatedBy(RejectTowerRentalProposalFlow::class)
class ReRejectTowerRentalProposalFlowResponder(val flowSession: FlowSession): FlowLogic<SignedTransaction>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        // Receiving information about anonymous identities
        subFlow(IdentitySyncFlow.Receive(flowSession))

        // signing transaction
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) {
            }
        }
        val txWeJustSignedId = subFlow(signedTransactionFlow)
        return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId = txWeJustSignedId.id))
    }
}

/**
 * Self issues the calling node an amount of cash in the desired currency.
 * Only used for demo/sample/training purposes!
 */
@InitiatingFlow
@StartableByRPC
class SelfIssueCashFlow(val amount: Amount<Currency>) : FlowLogic<Cash.State>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): Cash.State {
        /** Create the cash issue command. */
        val issueRef = OpaqueBytes.of(0)
        /** Note: ongoing work to support multiple notary identities is still in progress. */

        // Obtain a reference from a notary we wish to use.
        /**
         *  METHOD 1: Take first notary on network, WARNING: use for test, non-prod environments, and single-notary networks only!*
         *  METHOD 2: Explicit selection of notary by CordaX500Name - argument can by coded in flow or parsed from config (Preferred)
         *
         *  * - For production you always want to use Method 2 as it guarantees the expected notary is returned.
         */
        val notary = serviceHub.networkMapCache.notaryIdentities.single() // METHOD 1
        // val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")) // METHOD 2

        /** Create the cash issuance transaction. */
        val cashIssueTransaction = subFlow(CashIssueFlow(amount, issueRef, notary))
        /** Return the cash output. */
        return cashIssueTransaction.stx.tx.outputs.single().data as Cash.State
    }
}

/**
 * Self issues the calling node a Tower in the desired location.
 */
@InitiatingFlow
@StartableByRPC
class SelfIssueTowerFlow(val latitude: String,
                         val longitude: String,
                         val height: String,
                         val spec: String,
                         val totalSlots: Int,
                         val rentalAmount: Amount<Currency>,
                         val towerInfrastructureProvider: Party) : FlowLogic<TowerState>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): TowerState {
        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        // Create an instance of your output state
        val os: TowerState = TowerState(latitude,
                longitude,
                height,
                spec,
                totalSlots,
                rentalAmount,
                towerInfrastructureProvider)

        // Create Transaction Builder and call verify
        val transactionBuilder: TransactionBuilder = TransactionBuilder(notary = notary)
        transactionBuilder.verify(serviceHub);

        val transferCommand = Command(TowerRentalContract.Commands.IssueTower(), this.ourIdentity.owningKey)

        transactionBuilder.withItems(os,
                StateAndContract(os, TowerRentalContract.TOWER_CONTRACT_ID),
                transferCommand)

        transactionBuilder.verify(serviceHub)
        val selfSignedTx = serviceHub.signInitialTransaction(transactionBuilder)

        //Just pass an empty list of flow session in the finality flow.
        val emptyFlowSessions: Collection<FlowSession> = HashSet<FlowSession>();
        return subFlow(FinalityFlow(selfSignedTx, emptyList())).tx.outputsOfType(TowerState::class.java).single()
    }
}