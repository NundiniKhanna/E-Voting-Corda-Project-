package com.r3.developers.eVoting.workflows


import com.r3.developers.eVoting.contracts.CandidateContract
import com.r3.developers.eVoting.models.*
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

@InitiatingFlow(protocol = "declare-election-winner")
class DeclareElectionWinnerFlow : ClientStartableFlow {

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
        val notary = notaryLookup.notaryServices.single()

        val candidateStates = utxoLedgerService.findUnconsumedStatesByType(CandidateState::class.java)
        if (candidateStates.isEmpty()) {
            throw CordaRuntimeException("No candidates found.")
        }

        // Find candidate with highest vote count
        val winnerStateAndRef =
            candidateStates.maxByOrNull { it.state.contractState.candidateVoteCountModel?.voteCount ?: 0 }
                ?: throw CordaRuntimeException("Failed to determine the winner.")

        val winnerState = winnerStateAndRef.state.contractState
        val winningCandidateID = winnerState.candidateID

        // Create a new state to declare the winner
        val winnerDeclarationState = winnerState.copy(
            status = "Winner-Declared",
            candidateID = winningCandidateID
        )


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


        val participants = listOf(
            electionAuthority.ledgerKeys.first(),
            candidate.ledgerKeys.first(),
            auditor.ledgerKeys.first(),
            observer.ledgerKeys.first(),
            voter.ledgerKeys.first()

        )


        val transaction = utxoLedgerService.createTransactionBuilder()
            .setNotary(notary.name)
            .addInputState(winnerStateAndRef.ref)
            .addOutputState(winnerDeclarationState)
            .addCommand(CandidateContract.DeclareWinner())
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

            val finalizeResult = utxoLedgerService.finalize(
                transaction,
                listOf(
                    voterSession,
                    electionAuthoritySession,
                    candidateSession,
                    auditorSession,
                    observerSession
                ) // Include the voter session
            )

            return ResponseModel(
                transactionId,
                winnerState.candidateID,
                winnerState.candidateVoteCountModel?.voteCount,
                null
            ).toString()
        } catch (e: Exception) {
            return ResponseModel(null, null, null, "Flow failed, message: ${e.message}").toString()
        }
    }

    @InitiatedBy(protocol = "declare-election-winner")
    class DeclareElectionWinnerResponderFlow : ResponderFlow {

        @CordaInject
        lateinit var utxoLedgerService: UtxoLedgerService

        @Suspendable
        override fun call(session: FlowSession) {
            utxoLedgerService.receiveFinality(session) { transaction ->
                // Logic to handle the finality of the transaction if needed
            }
        }
    }
}
