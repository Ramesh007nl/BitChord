package com.music.bitchord.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template

class TanTovCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = object : Screen(carContext) {
        override fun onGetTemplate(): Template =
            MessageTemplate.Builder("TanTov Music is loading")
                .setHeader(Header.Builder().setTitle("TanTov Music").build())
                .build()
    }
}
