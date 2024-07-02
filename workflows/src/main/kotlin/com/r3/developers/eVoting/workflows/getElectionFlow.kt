package com.r3.developers.eVoting.workflows

import com.r3.developers.eVoting.contracts.VoterContract
//import com.r3.developers.serviceRequest.models.AcceptServiceRequestModel
import com.r3.developers.eVoting.models.VoterRegistrationModel
import com.r3.developers.eVoting.states.CandidateState
import com.r3.developers.eVoting.states.ElectionState
import com.r3.developers.eVoting.states.VoterState
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
import java.security.PublicKey
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
@InitiatingFlow(protocol = "Get-Election-Registration")
class getElectionFlow : ClientStartableFlow {

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var flowMessaging: FlowMessaging


    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject

    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val getRequest = requestBody.getRequestBodyAs(jsonMarshallingService, GetElectionRequestBody::class.java)
        val electionID = getRequest.electionID
        // Replace with actual input
        val notary = notaryLookup.notaryServices.single()

        val electionRegisStateAndRef = utxoLedgerService.findUnconsumedStatesByType(ElectionState::class.java)
            .filter { stateAndRef -> stateAndRef.state.contractState.electionID == electionID }
            .firstOrNull()
            ?: throw IllegalArgumentException("No state matching the electionId $electionID")

        val electionRegisStateData = electionRegisStateAndRef.state.contractState
        val responseData = "Election Request Data: $electionRegisStateData"


        try {
            return responseData

        } catch (e: Exception) {
            return "Flow failed, message: ${e.message}"

        }
    }
}

class GetElectionRequestBody (
    val electionID: UUID
)