package com.sgc.speedometer.data

import com.sgc.speedometer.data.prefs.PreferencesHelper
import com.sgc.speedometer.utils.SpeedUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppDataManager @Inject constructor(private val preferencesHelper: PreferencesHelper) : DataManager {

    override fun getMaxSpeed(): Int {
        return preferencesHelper.getMaxSpeed(0)
    }

    override fun getMaxSpeed(defaultMaxSpeed: Int): Int {
        return preferencesHelper.getMaxSpeed(defaultMaxSpeed)
    }

    override fun setMaxSpeed(maxSpeed: Int) {
        preferencesHelper.setMaxSpeed(maxSpeed)
    }

    override fun getSpeedUnit(defaultSpeedUnit: SpeedUnit): SpeedUnit {
        return preferencesHelper.getSpeedUnit(defaultSpeedUnit)
    }

    override fun setSpeedUnit(speedUnit: SpeedUnit) {
        preferencesHelper.setSpeedUnit(speedUnit)
    }
}