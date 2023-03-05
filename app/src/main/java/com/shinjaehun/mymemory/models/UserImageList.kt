package com.shinjaehun.mymemory.models

import com.google.firebase.firestore.PropertyName

data class UserImageList(
    @PropertyName("images") val images: List<String>? = null // @PropertyName은 뭐야?
)
