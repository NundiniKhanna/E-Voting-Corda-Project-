package com.r3.developers.eVoting.workflows
import com.r3.developers.eVoting.contracts.ElectionContract
import com.r3.developers.eVoting.contracts.VoterContract
import com.r3.developers.eVoting.models.*
import com.r3.developers.eVoting.states.VoterState
import com.r3.developers.eVoting.states.CandidateState
import com.r3.developers.eVoting.states.ElectionState
import net.corda.v5.application.flows.*
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
//import net.corda.v5.base.annotations.CordaInject
//import net.corda.v5.base.annotations.InitiatingFlow
//import net.corda.v5.base.annotations.InitiatedBy
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

private fun UUID.toInt() {

}
@InitiatingFlow(protocol = "doing-election-registration")
class ElectionFlow() : ClientStartableFlow {

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

        val request = requestBody.getRequestBodyAs(jsonMarshallingService, ElectionRegistrationModel::class.java)
        val uuid = UUID.randomUUID()
        val arrayOfCandidateID = request.candidateIDList
        val notary = notaryLookup.notaryServices.single()


        val filteredRequestIds = mutableListOf<UUID>()
        for (candidateID in arrayOfCandidateID) {
            val electionRequestStateAndRef = utxoLedgerService.findUnconsumedStatesByType(CandidateState::class.java)
                .firstOrNull { stateAndRef ->
                    stateAndRef.state.contractState.candidateID == candidateID
                }

            if (electionRequestStateAndRef != null) {
                // RequestId matches the filter criteria, add it to the filtered list
                filteredRequestIds.add(candidateID)
                val inputElectionRequestStateRef = electionRequestStateAndRef.ref
                val outputElectionRequestStateData = electionRequestStateAndRef.state.contractState

                // outputServiceRequestStateData.acceptServiceRequestModel= request
                //outputElectionRequestStateData.updatedOn = Instant.now().toEpochMilli()
                outputElectionRequestStateData.status = "created-election-request"
               // outputElectionRequestStateData.electionID = uuid
            } else {
                // RequestId not found, throw an exception or handle it accordingly
                throw IllegalArgumentException("No state matching the candidateId $candidateID")
            }
        }




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

        //val uuid = UUID.randomUUID()
        //val registrationDate = Instant.now().toEpochMilli()

        val  electionRequest= ElectionState(
            electionRegistrationModel = request,
            candidateIDList= filteredRequestIds,
            Status ="created-election-request",
            electionID = uuid,
            voterPublicKey =  voter.ledgerKeys.first(),
            electionAuthorityPublicKey= electionAuthority.ledgerKeys.first(),
            candidatePublicKey= candidate.ledgerKeys.first(),
            auditorPublicKey=  auditor.ledgerKeys.first(),
            observerPublicKey = observer.ledgerKeys.first(),
            participants = listOf(
                electionAuthority.ledgerKeys.first(),
                candidate.ledgerKeys.first(),
                auditor.ledgerKeys.first(),
                observer.ledgerKeys.first(),
                voter.ledgerKeys.first()

            )
        )
        // val voterSession = flowMessaging.initiateFlow(voterName)


        val transaction = utxoLedgerService.createTransactionBuilder()
            .setNotary(notary.name)
            .addOutputState(electionRequest)
            .addCommand(ElectionContract.CreateElection())
            .setTimeWindowUntil(Instant.now().plus(1, ChronoUnit.DAYS))
            .addSignatories(electionRequest.participants)
            .toSignedTransaction()

        val voterSession = flowMessaging.initiateFlow(voterName)
        val electionAuthoritySession = flowMessaging.initiateFlow(electionAuthorityName)
        val candidateSession = flowMessaging.initiateFlow(candidateName)
        val auditorSession = flowMessaging.initiateFlow(auditorName)
        val observerSession = flowMessaging.initiateFlow(observerName)



        try {

            val transactionId = transaction.id.toString().removePrefix("SHA-256D:")
            val electionID= uuid
            val finalizeResult =  utxoLedgerService.finalize(
                transaction,
                listOf(voterSession, electionAuthoritySession, candidateSession, auditorSession, observerSession) // Include the voter session
            )

            return ResponseModel3(transactionId, electionID , null).toString()
        } catch (e: Exception) {
            return ResponseModel3(null, null, "Flow failed, message: ${e.message}").toString()
        }
    }
}

@InitiatedBy(protocol = "doing-election-registration")
class ElectionRegistrationResponderFlow() : ResponderFlow {

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {

        utxoLedgerService.receiveFinality(session) { transaction ->

        }
    }
}
