package com.n2.moon.tower.flows


import co.paralleluniverse.fibers.Suspendable
import com.n2.moon.tower.contracts.TowerRentalContract
import com.n2.moon.tower.states.TowerRentalProposalState
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * This is the flow which handles transfers of existing Towers on the ledger.
 * Gathering the counterparty's signature is handled by the [CollectSignaturesFlow].
 * Notarisation (if required) and commitment to the ledger is handled by the [FinalityFlow].
 * The flow returns the [SignedTransaction] that was committed to the ledger.
 */
@InitiatingFlow
@StartableByRPC
class AgreeTowerRentalProposalFlow(val linearId: UniqueIdentifier,
                                   val newProposer: Party): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {

        // Stage 1. Retrieve Tower specified by linearId from the vault.
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
        val towerStateAndRef =  serviceHub.vaultService.queryBy<TowerRentalProposalState>(queryCriteria).states.single()
        val inputIou = towerStateAndRef.state.data

        // Stage 2. This flow can only be initiated by the current recipient.
        if (ourIdentity != inputIou.proposerParty) {
            throw IllegalArgumentException("Tower transfer can only be initiated by the Tower proposer.")
        }

        // Stage 3. Create the new Tower state reflecting a new proposer.
        val outputIou = inputIou.withNewProposer(newProposer)

        // Stage 4. Create the transfer command.
        val signers = (inputIou.participants + newProposer).map { it.owningKey }
        val transferCommand = Command(TowerRentalContract.Commands.AgreeTowerRentalAgreement(), signers)

        // Stage 5. Get a reference to a transaction builder.
        // Note: ongoing work to support multiple notary identities is still in progress.

        // Obtain a reference from a notary we wish to use.
        /**
         *  METHOD 1: Take first notary on network, WARNING: use for test, non-prod environments, and single-notary networks only!*
         *  METHOD 2: Explicit selection of notary by CordaX500Name - argument can by coded in flow or parsed from config (Preferred)
         *
         *  * - For production you always want to use Method 2 as it guarantees the expected notary is returned.
         */
        val notary = serviceHub.networkMapCache.notaryIdentities.single() // METHOD 1
        // val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")) // METHOD 2

        val builder = TransactionBuilder(notary = notary)

        // Stage 6. Create the transaction which comprises one input, one output and one command.
        builder.withItems(towerStateAndRef,
                StateAndContract(outputIou, TowerRentalContract.TOWER_CONTRACT_ID),
                transferCommand)

        // Stage 7. Verify and sign the transaction.
        builder.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(builder)

        // Stage 8. Collect signature from agreementParty and the new proposer and add it to the transaction.
        // This also verifies the transaction and checks the signatures.
        val sessions = (inputIou.participants - ourIdentity + newProposer).map { initiateFlow(it) }.toSet()
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        // Stage 9. Notarise and record the transaction in our vaults.
        return subFlow(FinalityFlow(stx, sessions))
    }
}

/**
 * This is the flow which signs Tower transfers.
 * The signing is handled by the [SignTransactionFlow].
 */
@InitiatedBy(AgreeTowerRentalProposalFlow::class)
class AgreeTowerRentalProposalFlowResponder(val flowSession: FlowSession): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an Tower transaction" using (output is TowerRentalProposalState)
            }
        }
        val txWeJustSignedId = subFlow(signedTransactionFlow)
        return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId = txWeJustSignedId.id))
    }
}