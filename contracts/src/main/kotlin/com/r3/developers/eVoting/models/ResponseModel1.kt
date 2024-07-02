package com.r3.developers.eVoting.models

import net.corda.v5.base.annotations.CordaSerializable
import java.util.*

@CordaSerializable
data class ResponseModel1(

    val transactionId: String?,
    val voterID: UUID?,
    val error : String?

)