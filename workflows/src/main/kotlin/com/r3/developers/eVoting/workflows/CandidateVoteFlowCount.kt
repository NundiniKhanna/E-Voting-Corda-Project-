package com.r3.developers.eVoting.workflows
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

import com.r3.developers.eVoting.contracts.CandidateContract
import com.r3.developers.eVoting.contracts.VoterContract
import com.r3.developers.eVoting.models.*
import com.r3.developers.eVoting.states.VoterState
import com.r3.developers.eVoting.states.CandidateState
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

@InitiatingFlow(protocol = "count-votes")
class CandidateVoteFlowCount : ClientStartableFlow {

    // Define your AES key (for demonstration, generate a new key each time)
    private val aesKey: SecretKey = generateAESKey()

    private fun generateAESKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256) // AES key size
        return keyGen.generateKey()
    }

    // Encrypts data using AES
    private fun encrypt(data: String): String {
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey)
        val encryptedBytes = cipher.doFinal(data.toByteArray())
        return Base64.getEncoder().encodeToString(encryptedBytes)
    }

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
        val request = requestBody.getRequestBodyAs(jsonMarshallingService, CandidateVoteCountModel::class.java)

        val uuid = UUID.randomUUID()
        val arrayOfVoterRequestIds = request.voterID
        var voteCount = request.voteCount ?: 0
        val notary = notaryLookup.notaryServices.single()
        val candidateID = request.candidateID
        val candidateStateAndRef = utxoLedgerService.findUnconsumedStatesByType(CandidateState::class.java).singleOrNull {
            it.state.contractState.candidateID == candidateID
        } ?: throw IllegalArgumentException("No candidate state found for candidate ID $candidateID")

        val candidateState = candidateStateAndRef.state.contractState

        val filteredVoterIDs = mutableListOf<UUID>()
        for (voterID in arrayOfVoterRequestIds) {
            val voterStateAndRef = utxoLedgerService.findUnconsumedStatesByType(VoterState::class.java).firstOrNull { stateAndRef ->
                stateAndRef.state.contractState.voterID == voterID
            }

            if (voterStateAndRef != null) {
                filteredVoterIDs.add(voterID)
                voteCount += 1

                // Example: Encrypt the vote count before updating state
                val encryptedVoteCount = encrypt(voteCount.toString())
                // Store or send `encryptedVoteCount` as needed

                val inputVoterStateRef = voterStateAndRef.ref
                val outputVoterStateData = voterStateAndRef.state.contractState
                outputVoterStateData.candidateID = uuid
                outputVoterStateData.status = "count-votes"
            } else {
                throw IllegalArgumentException("No state matching the requestId $voterID")
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

        val voterName = MemberX500Name.parse("CN=Voter, OU=Public, O=R3, L=London, C=GB")
        val voter = memberLookup.lookup(voterName)
            ?: throw CordaRuntimeException("MemberLookup can't find voter specified in flow arguments.")

        val participants = listOf(
            electionAuthority.ledgerKeys.first(),
            candidate.ledgerKeys.first(),
            auditor.ledgerKeys.first(),
            observer.ledgerKeys.first(),
            voter.ledgerKeys.first()
        )

        val transaction = utxoLedgerService.createTransactionBuilder()
            .setNotary(notary.name)
            .addInputState(candidateStateAndRef.ref)
            .addOutputState(candidateState)
            .addCommand(CandidateContract.UpdateVoteCount())
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
            val candidateID = uuid
            val finalizeResult = utxoLedgerService.finalize(
                transaction,
                listOf(voterSession, electionAuthoritySession, candidateSession, auditorSession, observerSession)
            )

            return ResponseModel4(transactionId, candidateID, voteCount, null).toString()
        } catch (e: Exception) {
            return ResponseModel4(null, null, null, "Flow failed, message: ${e.message}").toString()
        }
    }
}
