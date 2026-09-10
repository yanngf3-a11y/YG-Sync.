package com.ygsync.controller.youtube

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class YgYouTubeViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (modelClass.isAssignableFrom(
                YgYouTubeViewModel::class.java
            )
        ) {

            val engine =
                YgYouTubeEngine(
                    context.applicationContext
                )

            val repository =
                YgYouTubeRepository(
                    engine
                )

            return YgYouTubeViewModel(
                repository
            ) as T
        }

        throw IllegalArgumentException(
            "ViewModel desconocido: ${modelClass.name}"
        )
    }
}
