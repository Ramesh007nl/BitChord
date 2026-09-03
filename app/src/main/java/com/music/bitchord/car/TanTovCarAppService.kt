package com.music.bitchord.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import com.music.bitchord.BuildConfig
import com.music.bitchord.R

class TanTovCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        if (BuildConfig.DEBUG) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(this).addAllowedHosts(R.array.hosts_allowlist).build()
        }

    override fun onCreateSession(sessionInfo: SessionInfo): Session = TanTovCarSession()
}
