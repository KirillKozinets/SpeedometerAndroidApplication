package com.sgc.speedometer.ui.speedometer

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager.PROVIDERS_CHANGED_ACTION
import android.net.Uri
import android.os.*
import android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.getActionButton
import com.afollestad.materialdialogs.input.input
import com.kobakei.ratethisapp.RateThisApp
import com.sgc.speedometer.App
import com.sgc.speedometer.BR
import com.sgc.speedometer.ISpeedometerService
import com.sgc.speedometer.R
import com.sgc.speedometer.data.DataManager
import com.sgc.speedometer.data.model.Date
import com.sgc.speedometer.SpeedometerRecord
import com.sgc.speedometer.data.service.SpeedometerService
import com.sgc.speedometer.data.util.distanceUnit.DistanceUnitConverter
import com.sgc.speedometer.data.util.speedUnit.SpeedUnitConverter
import com.sgc.speedometer.databinding.ActivitySpeedometerBinding
import com.sgc.speedometer.di.component.ActivityComponent
import com.sgc.speedometer.ui.base.BaseActivity
import com.sgc.speedometer.ui.customView.speedometer.render.RoundSpeedometerRender
import com.sgc.speedometer.ui.customView.speedometer.render.TextSpeedometerRender
import com.sgc.speedometer.ui.history.HistoryActivity
import com.sgc.speedometer.ui.settings.SettingsActivity
import com.sgc.speedometer.ui.speedometer.speedLimitControl.SpeedLimitControlObserver
import com.sgc.speedometer.utils.AppConstants
import com.sgc.speedometer.utils.AppConstants.CONTINUE_RECORD
import com.sgc.speedometer.utils.AppConstants.SPEED_INTENT_FILTER
import com.sgc.speedometer.utils.AppConstants.TAG_CODE_PERMISSION_LOCATION
import kotlinx.android.synthetic.main.activity_speedometer.*
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

class SpeedometerActivity : BaseActivity<ActivitySpeedometerBinding, SpeedometerViewModel>(),
    SpeedLimitControlObserver {

    override val layoutId: Int
        get() = R.layout.activity_speedometer

    private var vibrator: Vibrator? = null

    @Inject
    lateinit var dataManager: DataManager

    @Inject
    lateinit var speedUnitConverter: SpeedUnitConverter

    @Inject
    lateinit var distanceUnitConverter: DistanceUnitConverter

    private var isPause = false

    override fun performDataBinding() {
        viewDataBinding = DataBindingUtil.setContentView(this, layoutId)
        viewDataBinding!!.lifecycleOwner = this
        viewDataBinding!!.setVariable(BR.speedometerViewModel, viewModel)
        viewDataBinding!!.setVariable(BR.distanceUnitConverter, distanceUnitConverter)
        viewDataBinding!!.setVariable(BR.speedUnitConverter, speedUnitConverter)
        viewDataBinding!!.executePendingBindings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = ""
        selectTheme(dataManager.getIsDarkTheme())
        initSpeedLimitClickListener()
        checkGPSEnable()
        if (savedInstanceState == null) {
            requestPermissions()
            checkOptimization()
        }
        restoreState(savedInstanceState)
    }

    @SuppressLint("NewApi", "BatteryLife")
    private fun checkOptimization() {
        val packageName = applicationContext.packageName
        val pm = applicationContext.getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent()
            intent.action = ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            intent.data = Uri.parse("package:" + this.packageName)
            this.startActivity(intent)
        }
    }

    override fun performDependencyInjection(buildComponent: ActivityComponent) {
        buildComponent.inject(this)
    }

    override fun onResume() {
        super.onResume()
        speedometer.speedUnit = dataManager.getSpeedUnit()
        speedometer.speedometerResolution = dataManager.getSpeedometerResolution().speedResolution
        distance_unit.text = dataManager.getDistanceUnit().getString(this)
        average_unit.text = dataManager.getSpeedUnit().getString(this)
        max_unit.text = dataManager.getSpeedUnit().getString(this)
    }

    private fun selectTheme(isDarkTheme: Boolean) {
        when (isDarkTheme) {
            true ->
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            false ->
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this, arrayOf(
                ACCESS_FINE_LOCATION,
                ACCESS_COARSE_LOCATION
            ),
            TAG_CODE_PERMISSION_LOCATION
        )
    }

    private fun registerReceivers() {
        val speedReceiver = SpeedReceiver()
        registerReceiver(speedReceiver, IntentFilter(SPEED_INTENT_FILTER))
        val gpsSwitchStateReceiver = GPSSwitchStateReceiver()
        registerReceiver(gpsSwitchStateReceiver, IntentFilter(PROVIDERS_CHANGED_ACTION))
    }

    var isSpeedLimitDialogOpen = false

    private fun initSpeedLimitClickListener() {
        speedLimit.setOnClickListener {
            MaterialDialog(this).show {
                title(R.string.set_speed_limit)
                negativeButton { }
                apply {
                    getActionButton(WhichButton.POSITIVE).updateTextColor(getColor(R.color.text_color))
                    getActionButton(WhichButton.NEGATIVE).updateTextColor(getColor(R.color.text_color))
                }
                input(
                    inputType = InputType.TYPE_CLASS_NUMBER,
                    prefill = viewModel.maxSpeed.value.toString(),
                    maxLength = 5
                ) { _, text ->
                    viewModel.updateMaxSpeed(text.toString().toInt())
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            TAG_CODE_PERMISSION_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    registerReceivers()
                    startService()
                    viewModel.setSpeedLimitControlObserver(this)
                    RateThisApp.onCreate(this)
                    RateThisApp.showRateDialogIfNeeded(this)
                } else {
                    MaterialDialog(this@SpeedometerActivity).show {
                        message(R.string.ask_permission)
                        positiveButton { requestPermissions() }
                        apply {
                            getActionButton(WhichButton.POSITIVE).updateTextColor(getColor(R.color.text_color))
                        }
                    }
                }
            }
        }
    }

    private fun continueRecord() {
        val record = intent.getParcelableExtra<SpeedometerRecord>(CONTINUE_RECORD)
        if (record != null) {
            service!!.continueRecord(record)
        }
    }

    override fun speedLimitExceeded() {
        if (dataManager.getIsVibration())
            vibrate()
        speedometer.isSpeedLimitExceeded = true
    }

    private fun vibrate() {
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator!!.vibrate(VibrationEffect.createOneShot(4000, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator!!.vibrate(4000)
        }
    }

    override fun speedLimitReturned() {
        vibrator?.cancel()
        speedometer.isSpeedLimitExceeded = false
    }

    override fun onDestroy() {
        vibrator?.cancel()
        super.onDestroy()
    }

    private var service: ISpeedometerService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = ISpeedometerService.Stub.asInterface(service)
            this@SpeedometerActivity.service = binder
            continueRecord()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {}
    }

    private fun startService() {
        val serviceIntent = Intent(this, SpeedometerService::class.java)
        bindService(serviceIntent, connection, Context.BIND_IMPORTANT)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        if (speedometer.speedometerRender.getRenderId() == 0 && isPause)
            inflater.inflate(R.menu.round_speedometer_menu_pause, menu)
        else if (speedometer.speedometerRender.getRenderId() == 0 && !isPause)
            inflater.inflate(R.menu.round_speedometer_menu, menu)
        else if (speedometer.speedometerRender.getRenderId() == 1 && isPause)
            inflater.inflate(R.menu.text_speedometer_menu_pause, menu)
        else
            inflater.inflate(R.menu.text_speedometer_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings -> {
                startAnimActivity(SettingsActivity::class.java)
            }
            R.id.reset -> {
                if (service != null)
                    service!!.reset()
            }
            R.id.set_round_speedometer -> {
                speedometer.speedometerRender = RoundSpeedometerRender(this)
                invalidateOptionsMenu()
            }
            R.id.set_text_speedometer -> {
                speedometer.speedometerRender = TextSpeedometerRender(this)
                invalidateOptionsMenu()
            }
            R.id.start -> {
                if (service != null) {
                    service!!.start()
                    isPause = false
                    invalidateOptionsMenu()
                }
            }
            R.id.pause -> {
                if (service != null) {
                    service!!.stop()
                    isPause = true
                    invalidateOptionsMenu()
                }
            }
            R.id.addHistory -> {
                val record = viewModel.speedometerRecord.value
                record!!.recordDate =
                    Date(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                val id = dataManager.insertSpeedometerRecord(record)
                service?.setRecordId(id)
                startAnimActivity(HistoryActivity::class.java)
            }
            R.id.history -> {
                startAnimActivity(HistoryActivity::class.java)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun startAnimActivity(cls: Class<*>) {
        val intent = Intent(this, cls)
        startActivity(intent)
        overridePendingTransition(R.anim.enter, R.anim.exit)
    }

    private fun checkGPSEnable() {
        val isGPSEnable = viewModel.getIsGPSEnable(this)
        showGPSEnableDialog(isGPSEnable)
        speedometer.gpsEnable = isGPSEnable
    }

    private fun showGPSEnableDialog(isGPSEnable: Boolean) {
        if (!isGPSEnable) {
            if ((application as App).isAppForeground)
                try {
                    MaterialDialog(this@SpeedometerActivity).show {
                        title(R.string.gps_disable)
                        message(R.string.turn_on_gps)
                        negativeButton { }
                        positiveButton { startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)); }
                        apply {
                            getActionButton(WhichButton.POSITIVE).updateTextColor(getColor(R.color.text_color))
                            getActionButton(WhichButton.NEGATIVE).updateTextColor(getColor(R.color.text_color))
                        }
                    }
                } catch (ex: WindowManager.BadTokenException) {
                    //Ignore
                }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(SPEEDOMETER_RENDER_ID, speedometer.speedometerRender.getRenderId())
        outState.putBoolean(IS_PAUSE, isPause)
        super.onSaveInstanceState(outState)
    }

    private fun restoreState(state: Bundle?) {
        if (state != null) {
            isPause = state.getBoolean(IS_PAUSE)
            val speedometerRenderId = state.getInt(SPEEDOMETER_RENDER_ID)
            if (speedometerRenderId == 1)
                speedometer.speedometerRender = TextSpeedometerRender(this)
            val serviceIntent = Intent(this, SpeedometerService::class.java)
            bindService(serviceIntent, connection, Context.BIND_IMPORTANT)
        }
    }

    private inner class SpeedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action.equals(SPEED_INTENT_FILTER)) {
                val speedometerRecord =
                    intent.getParcelableExtra<SpeedometerRecord>(AppConstants.SPEEDOMETER_RECORD_KEY)
                viewModel.updateSpeedometerRecord(speedometerRecord!!)
            }
        }
    }

    private inner class GPSSwitchStateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (PROVIDERS_CHANGED_ACTION == intent.action) {
                checkGPSEnable()
            }
        }
    }

    companion object {
        private const val SPEEDOMETER_RENDER_ID = "SPEEDOMETER_RENDER_ID"
        private const val IS_PAUSE = "IS_PAUSE"
    }
}