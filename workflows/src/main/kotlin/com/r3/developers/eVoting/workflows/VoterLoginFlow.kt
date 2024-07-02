package com.r3.developers.eVoting.workflows


import com.r3.developers.eVoting.contracts.VoterContract
import com.r3.developers.eVoting.models.LoginModel
import com.r3.developers.eVoting.models.ResponseModel1
import com.r3.developers.eVoting.states.VoterState
import com.r3.developers.eVoting.models.VoterRegistrationModel
import net.corda.v5.application.flows.*
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

private fun UUID.toInt() {

}

    @InitiatingFlow(protocol = "Login-Voter")
class VoterLoginFlow(): ClientStartableFlow {

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val request = requestBody.getRequestBodyAs(jsonMarshallingService, LoginModel::class.java)


        val voterID = request.voterID
       // val assignedOn = Instant.now().toEpochMilli()
        //  val batchId = request.batchId
        val notary = notaryLookup.notaryServices.single()

        val voterStateAndRef = utxoLedgerService.findUnconsumedStatesByType(VoterState::class.java)
            .firstOrNull { stateAndRef -> stateAndRef.state.contractState.voterID == voterID }
            ?: throw IllegalArgumentException("No state matching the voterID $voterID")

        val inputVoterStateRef = voterStateAndRef.ref
//        if (inputServiceRequestStateRef.status != "Created") {
//            return "Service request with requestId $requestId is not in 'Created' status. Acceptance is not allowed."
//        }
        val outputVoterStateData = voterStateAndRef.state.contractState
//        if (outputServiceRequestStateData.status != "Created") {
//            return "Service request with requestId $requestId has an 'accepted' status."
//        }


        outputVoterStateData.loginModel= request
       // outputServiceRequestStateData.updatedOn = Instant.now().toEpochMilli()
        //outputServiceRequestStateData.assignedOn = Instant.now().toEpochMilli()
        outputVoterStateData.status ="Voter-Logged-in"


        val electionAuthorityName = MemberX500Name.parse("CN=ElectionAuthority, OU=Government, O=R3, L=London, C=GB")
        val electionAuthority = memberLookup.lookup(electionAuthorityName)
            ?: throw CordaRuntimeException("MemberLookup can't find election authority specified in flow arguments.")

        val candidateName = MemberX500Name.parse("CN=Candidate, OU=PoliticalParty, O=R3, L=London, C=GB")
        val candidate = memberLookup.lookup(candidateName)
            ?: throw CordaRuntimeException("MemberLookup can't find candidate specified in flow arguments.")

        val auditorName = MemberX500Name.parse("CN=Auditor, OU=Auditing, O=R3, L=London, C=GB")
        val auditor = memberLookup.lookup(auditorName)
            ?: throw CordaRuntimeException("MemberLookup can't find auditor specified in flow arguments.")

        val observerName = MemberX500Name.parse("CN=Observer, OU=Monitoring, O=R3, L=London, C=GB")
        val observer = memberLookup.lookup(observerName)
            ?: throw CordaRuntimeException("MemberLookup can't find observer specified in flow arguments.")

        // Add the voter as a participant
        val voterName = MemberX500Name.parse("CN=Voter, OU=Public, O=R3, L=London, C=GB")
        val voter = memberLookup.lookup(voterName)
            ?: throw CordaRuntimeException("MemberLookup can't find voter specified in flow arguments.")

        val  participants = listOf(
            electionAuthority.ledgerKeys.first(),
            candidate.ledgerKeys.first(),
            auditor.ledgerKeys.first(),
            observer.ledgerKeys.first(),
            voter.ledgerKeys.first()

        )



        val transaction = utxoLedgerService.createTransactionBuilder()
            .setNotary(notary.name)
            .addInputState(inputVoterStateRef)
            .addOutputState(outputVoterStateData)
            .addCommand(VoterContract.Login())
            .setTimeWindowUntil(Instant.now().plus(1, ChronoUnit.DAYS))
            .addSignatories(participants)
            .toSignedTransaction()

        val voterSession = flowMessaging.initiateFlow(voterName)
        val electionAuthoritySession = flowMessaging.initiateFlow(electionAuthorityName)
        val candidateSession = flowMessaging.initiateFlow(candidateName)
        val auditorSession = flowMessaging.initiateFlow(auditorName)
        val observerSession = flowMessaging.initiateFlow(observerName)

        try {
            val transactionId = transaction.id.toString().removePrefix("SHA-256D:")
            // val requestId= uuid// Set the actual requestId based on your business logic
            val finalizeResult =
                utxoLedgerService.finalize(transaction, listOf(voterSession, electionAuthoritySession, candidateSession, auditorSession, observerSession) ).toString()

            // Return the ServiceRequestResponse with transactionId, requestId, and no error
            return ResponseModel1(transactionId, voterID, null).toString()
        } catch (e: Exception) {
            // Return the ServiceRequestResponse with transactionId, empty requestId, and the error message
            return ResponseModel1(null,null,"Flow failed, message: ${e.message}").toString()
        }
    }


}
@InitiatedBy(protocol = "Login-Voter")
class VoterLoginResponderFlow: ResponderFlow {

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {
        // Receive, verify, validate, sign and record the transaction sent from the initiator
        utxoLedgerService.receiveFinality(session) {transaction ->
            /*
             * [receiveFinality]
will automatically verify the transaction and its signatures before signing it.
             * However, just because a transaction is contractually valid doesn't mean we necessarily want to sign.
             * What if we don't want to deal with the counterparty in question, or the value is too high,
             * or we're not happy with the transaction's structure? [UtoTransactionValidator] (the lambda created
             * here) allows us to define the additional checks. If any of these conditions are not met,
             * we will not sign the transaction - even if the transaction and its signatures are contractually valid.
             */
        }
    }
}
