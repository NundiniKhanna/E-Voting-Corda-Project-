package com.r3.developers.eVoting.models
import net.corda.v5.base.annotations.CordaSerializable
import java.security.PublicKey
import java.util.*
@CordaSerializable
data class CandidateProfile(
    val candidateName: String,
    val partyName: String,
    val age: Int,
    val education: String,
    val experience: String
)
