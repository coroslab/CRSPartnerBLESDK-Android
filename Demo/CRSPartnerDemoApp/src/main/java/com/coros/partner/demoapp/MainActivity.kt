package com.coros.partner.demoapp

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import com.coros.partner.ble.sdk.api.CRSPartnerBLESDK
import com.coros.partner.ble.sdk.api.callback.OnDeviceConnectionStateChangeCallback
import com.coros.partner.ble.sdk.api.callback.OnHeartRateChangeCallback
import com.coros.partner.ble.sdk.api.enums.DeviceConnectionState
import com.coros.partner.ble.sdk.api.enums.Environment
import com.coros.partner.ble.sdk.api.enums.Scope
import com.coros.partner.ble.sdk.api.model.AuthorizationRequest
import com.coros.partner.ble.sdk.api.model.AuthorizedDevice
import com.coros.partner.ble.sdk.api.model.AuthorizedDeviceWithConnection
import com.coros.partner.ble.sdk.api.model.HeartBroadcastResult
import com.coros.partner.ble.sdk.api.model.HrSample
import com.coros.partner.ble.sdk.api.model.LogConfig
import com.coros.partner.ble.sdk.api.model.PartnerBleSdkException
import com.coros.partner.ble.sdk.api.model.SdkInitConfig
import com.coros.partner.demoapp.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private companion object {
        const val DEFAULT_CLIENT_ID = "ble_sdk_it_1778767839068"
        const val DEFAULT_PARTNER_USER_ID = "partner_user_id"
        const val MILLIS_PER_SECOND = 1000L
    }

    private lateinit var binding: ActivityMainBinding
    private var heartBroadcastResult: HeartBroadcastResult? = null
    private var initialized = false
    private val consoleLog = StringBuilder()
    private val deviceConnectionStateCallback = object : OnDeviceConnectionStateChangeCallback {
        override fun onChange(
            deviceName: String,
            deviceModel: String,
            connectionState: DeviceConnectionState,
        ) {
            appendConsole(
                R.string.console_device_connection_state_change,
                deviceName,
                deviceModel,
                connectionState
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupInputs()
        setupActions()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onDestroy() {
        CRSPartnerBLESDK.removeDeviceConnectionStateCallback(deviceConnectionStateCallback)
        heartBroadcastResult?.stop()
        heartBroadcastResult = null
        CRSPartnerBLESDK.release()
        super.onDestroy()
    }

    private fun setupInputs() {
        binding.etClientId.setText(DEFAULT_CLIENT_ID)
        binding.etPartnerUserId.setText(DEFAULT_PARTNER_USER_ID)
        renderStatus(R.string.status_empty)
    }

    private fun setupActions() {
        binding.btnInitialize.setOnClickListener { initializeSdk() }
        binding.btnClearConsole.setOnClickListener { clearConsole() }
        binding.btnAuthorize.setOnClickListener {
            runSdkAction(R.string.status_authorizing) {
                appendConsole(R.string.console_authorize_call, Scope.BLE_HEART_BROADCAST.name)
                val result = CRSPartnerBLESDK.authorize(
                    AuthorizationRequest(scope = Scope.BLE_HEART_BROADCAST)
                )
                renderStatus(R.string.status_authorize_success, result.devices.size)
                appendConsole(R.string.console_authorize_result, result.devices.size)
                appendConsole(formatDevices(result.devices))
            }
        }
        binding.btnListDevices.setOnClickListener {
            runSdkAction(R.string.status_listing_devices) {
                appendConsole(R.string.console_list_devices_call)
                val devices = CRSPartnerBLESDK.listAuthorizedDevices()
                renderStatus(R.string.status_list_devices_success, devices.size)
                appendConsole(R.string.console_list_devices_result, devices.size)
                appendConsole(formatDevicesWithConnection(devices))
            }
        }
        binding.btnAddConnectionCallback.setOnClickListener {
            runSdkAction(R.string.status_adding_connection_callback) {
                appendConsole(R.string.console_add_connection_callback_call)
                CRSPartnerBLESDK.addDeviceConnectionStateCallback(deviceConnectionStateCallback)
                renderStatus(R.string.status_connection_callback_added)
            }
        }
        binding.btnRemoveConnectionCallback.setOnClickListener {
            runSdkAction(R.string.status_removing_connection_callback) {
                appendConsole(R.string.console_remove_connection_callback_call)
                CRSPartnerBLESDK.removeDeviceConnectionStateCallback(deviceConnectionStateCallback)
                renderStatus(R.string.status_connection_callback_removed)
            }
        }
        binding.btnStartHr.setOnClickListener {
            val sportStartUtc = currentSportStartUtc()
            runSdkAction(R.string.status_starting_heart_rate) {
                heartBroadcastResult?.stop()
                appendConsole(R.string.console_start_heart_rate_call, sportStartUtc)
                heartBroadcastResult = CRSPartnerBLESDK.startHeartBroadcast(
                    sportStartTimeInSec = sportStartUtc,
                    callback = object : OnHeartRateChangeCallback {
                        override fun onChange(hrSample: HrSample) {
                            lifecycleScope.launch {
                                appendConsole(formatHrSample(hrSample))
                            }
                        }

                        override fun onFailure(exception: PartnerBleSdkException) {
                            heartBroadcastResult = null
                            renderStatus(formatError(exception))
                        }
                    }
                )
                renderStatus(R.string.status_heart_rate_started, sportStartUtc)
            }
        }
        binding.btnStopHr.setOnClickListener {
            val result = heartBroadcastResult
            if (result == null) {
                renderStatus(R.string.status_heart_rate_not_started)
                return@setOnClickListener
            }
            result.stop()
            heartBroadcastResult = null
            appendConsole(R.string.console_stop_heart_rate_call)
            renderStatus(R.string.status_heart_rate_stopped)
        }
        binding.btnRevoke.setOnClickListener {
            runSdkAction(R.string.status_revoking) {
                heartBroadcastResult?.stop()
                heartBroadcastResult = null
                appendConsole(R.string.console_revoke_call)
                CRSPartnerBLESDK.revoke()
                renderStatus(R.string.status_revoke_success)
                appendConsole(R.string.console_revoke_done)
            }
        }
    }

    private fun initializeSdk() {
        val clientId = binding.etClientId.text?.toString()?.trim().orEmpty()
        val partnerUserId = binding.etPartnerUserId.text?.toString()?.trim().orEmpty()
        beginConsoleOperation()
        if (clientId.isBlank()) {
            renderStatus(R.string.status_client_id_required)
            return
        }
        if (partnerUserId.isBlank()) {
            renderStatus(R.string.status_partner_user_id_required)
            return
        }
        appendConsole(R.string.console_initialize_call, clientId, partnerUserId, selectedEnvironment().name)
        runCatching {
            CRSPartnerBLESDK.initialize(
                context = applicationContext,
                config = SdkInitConfig(
                    clientId = clientId,
                    partnerUserId = partnerUserId,
                    environment = selectedEnvironment(),
                    logConfig = LogConfig(
                        enableConsoleLog = true,
                        enableFileLog = true,
                    ),
                )
            )
        }.onSuccess {
            initialized = true
            renderStatus(R.string.status_initialized, selectedEnvironmentName())
        }.onFailure {
            renderStatus(formatError(it))
        }
    }

    private fun runSdkAction(
        @StringRes loadingTextRes: Int,
        startOperation: Boolean = true,
        requireInitialized: Boolean = true,
        action: suspend () -> Unit
    ) {
        if (startOperation) {
            beginConsoleOperation()
        }
        if (requireInitialized && !initialized) {
            renderStatus(R.string.status_sdk_not_initialized)
            return
        }
        renderStatus(loadingTextRes)
        lifecycleScope.launch {
            runCatching { action() }
                .onFailure { throwable ->
                    val message = formatError(throwable)
                    renderStatus(message)
                }
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        beginConsoleOperation()
        appendConsole(
            R.string.console_handle_open_url_payload,
            uri.getQueryParameter("result").orEmpty(),
            uri.getQueryParameter("state").orEmpty(),
            uri.getQueryParameter("errorCode").orEmpty(),
            uri.getQueryParameter("errorMessage").orEmpty()
        )
        runSdkAction(R.string.status_handling_callback, startOperation = false) {
            appendConsole(R.string.console_handle_open_url_call, uri.toString())
            val result = CRSPartnerBLESDK.handleOpenURL(uri.toString())
            renderStatus(R.string.status_handle_callback_success, result.devices.size)
            appendConsole(R.string.console_handle_open_url_result, result.devices.size)
            appendConsole(formatDevices(result.devices))
        }
    }

    private fun selectedEnvironment(): Environment {
        return if (binding.rbEnvironmentProduction.isChecked) {
            Environment.PRODUCTION
        } else {
            Environment.TEST
        }
    }

    private fun selectedEnvironmentName(): String {
        return if (binding.rbEnvironmentProduction.isChecked) {
            getString(R.string.environment_name_production)
        } else {
            getString(R.string.environment_name_test)
        }
    }

    private fun currentSportStartUtc(): Long {
        return System.currentTimeMillis() / MILLIS_PER_SECOND
    }

    private fun renderStatus(@StringRes resId: Int, vararg args: Any) {
        appendConsole(getString(resId, *args))
    }

    private fun renderStatus(status: String) {
        appendConsole(status)
    }

    private fun clearConsole() {
        consoleLog.clear()
        binding.tvConsole.text = ""
        binding.consoleScroll.post {
            binding.consoleScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun beginConsoleOperation() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread { beginConsoleOperation() }
            return
        }
        if (consoleLog.isNotEmpty()) {
            consoleLog.append('\n')
            binding.tvConsole.text = consoleLog
        }
    }

    private fun appendConsole(@StringRes resId: Int, vararg args: Any) {
        appendConsole(getString(resId, *args))
    }

    private fun appendConsole(text: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread { appendConsole(text) }
            return
        }
        if (consoleLog.isNotEmpty()) {
            consoleLog.append('\n')
        }
        consoleLog.append(text)
        binding.tvConsole.text = consoleLog
        binding.consoleScroll.post {
            binding.consoleScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun formatDevicesWithConnection(devices: List<AuthorizedDeviceWithConnection>): String {
        if (devices.isEmpty()) {
            return getString(R.string.devices_empty)
        }
        return devices.joinToString(
            separator = "\n",
            prefix = getString(R.string.devices_title) + "\n"
        ) { device ->
            "deviceName=${device.deviceName}, deviceModel=${device.deviceModel}, connectionState=${device.connectionState}"
        }
    }

    private fun formatDevices(devices: List<AuthorizedDevice>): String {
        if (devices.isEmpty()) {
            return getString(R.string.devices_empty)
        }
        return devices.joinToString(
            separator = "\n",
            prefix = getString(R.string.devices_title) + "\n"
        ) { device ->
            "deviceName=${device.deviceName}, deviceModel=${device.deviceModel}"
        }
    }

    private fun formatHrSample(sample: HrSample): String {
        return getString(
            R.string.console_heart_rate_sample,
            sample.heartRate,
            sample.timestampInSec,
            sample.sportStartTimeInSec
        )
    }

    private fun formatError(throwable: Throwable): String {
        val sdkException = throwable as? PartnerBleSdkException
        return if (sdkException != null) {
            getString(
                R.string.error_sdk,
                sdkException.code.value,
                sdkException.serverCode.orEmpty(),
                sdkException.traceId.orEmpty(),
                sdkException.message.orEmpty()
            )
        } else {
            getString(R.string.error_unknown, throwable.message ?: throwable::class.java.simpleName)
        }
    }

}
