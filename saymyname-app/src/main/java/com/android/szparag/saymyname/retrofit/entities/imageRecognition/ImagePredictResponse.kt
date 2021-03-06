package com.android.szparag.saymyname.retrofit.entities.imageRecognition

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName


/**
 * Created by Przemyslaw Jablonski (github.com/sharaquss, pszemek.me) on 7/1/2017.
 */
data class ImagePredictResponse(
    @SerializedName("status") @Expose var status: Status? = null,
    @SerializedName("outputs") @Expose val outputs: List<Output>
)