package com.sgc.speedometer.data.prefs

import com.sgc.speedometer.utils.SpeedUnit

interface PreferencesHelper {

    fun getMaxSpeed(): Int

    fun getMaxSpeed(defaultMaxSpeed:Int): Int

    fun setMaxSpeed(maxSpeed: Int)

    fun getSpeedUnit(defaultSpeedUnit: SpeedUnit): SpeedUnit

    fun setSpeedUnit(speedUnit: SpeedUnit)
}