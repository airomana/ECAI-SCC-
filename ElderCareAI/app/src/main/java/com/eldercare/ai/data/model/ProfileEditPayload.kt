package com.eldercare.ai.data.model

data class ProfileEditPayload(
    val name: String = "",
    val sex: String = "",
    val age: Int = 0,
    val birthYear: Int = 0,
    val diseases: List<String> = emptyList(),
    val allergies: List<String> = emptyList(),
    val dietRestrictions: List<String> = emptyList()
)
