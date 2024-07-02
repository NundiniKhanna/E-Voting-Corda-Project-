package com.r3.developers.eVoting.models

import net.corda.v5.base.annotations.CordaSerializable
import java.security.PublicKey
import java.util.*
@CordaSerializable
data class ElectionRegistrationModel (

    val electionName: String,
    val status: String,
    val startDate: Long? =null,
    val endDate: Long? = null,
    val candidateIDList: MutableList<UUID>,
)