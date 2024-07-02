package com.r3.developers.eVoting.models


import net.corda.v5.base.annotations.CordaSerializable

import java.util.UUID

@CordaSerializable
data class LoginModel (
    val email: String,
    val voterID: UUID
)