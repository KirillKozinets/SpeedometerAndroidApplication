package com.sgc.speedometer.ui.customView.speedometer

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import com.sgc.speedometer.ui.customView.speedometer.render.SpeedometerRender
import com.sgc.speedometer.ui.customView.speedometer.render.TextSpeedometerRender
import com.sgc.speedometer.utils.SpeedUnit

class SpeedometerView : View {

    var speedometerRender: SpeedometerRender = TextSpeedometerRender(context)

    var speed: Int = 0
        set(value) {
            field = value
            invalidate()
        }
    var maxSpeed: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)
    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    override fun onDraw(canvas: Canvas) {
        speedometerRender.draw(canvas,speed,maxSpeed,SpeedUnit.KilometerPerHour)
        super.onDraw(canvas)
    }

    fun speedLimitExceeded(){
        speedometerRender.speedLimitExceeded()
    }

    fun speedLimitReturned(){
        speedometerRender.speedLimitReturned()
    }

}