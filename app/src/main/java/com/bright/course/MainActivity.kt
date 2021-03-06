package com.bright.course

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.util.Log
import android.view.View
import com.bright.course.config.AppManagerActivity
import com.bright.course.config.SystemConfigDialog
import com.bright.course.home.HomeAppCenterFragment
import com.bright.course.mqtt.MQTTBaseActivity
import com.bright.course.mqtt.MQTTHelper
import com.bright.course.utils.DialogUtil.Companion.initHiddenMainDialog
import com.bright.course.utils.NetWorkUtil.Companion.isNetworkConnected
import com.bright.course.utils.ScreenOffAdminReceiver
import com.bright.course.utils.StreamUtils
import com.bright.course.utils.ToastGlobal
import com.bright.course.video.VideoActivity
import com.bright.course.wifi.WifiListFragment
import com.classroom.mvp.presenter.LoginOutPresenter
import com.rztop.classroom.classroom.mvp.contract.LoginOutContract
import com.screen.FloatingFinger
import kotlinx.android.synthetic.main.activity_fullscreen.*
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.intentFor
import org.jetbrains.anko.uiThread
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.*


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */

class MainActivity : MQTTBaseActivity(), LoginOutContract.View {


    lateinit var adminReceiver: ComponentName
    lateinit var timer: Timer
    lateinit var timerTask: TimerTask
    var TAG = MainActivity::class.java.simpleName
    var isShow = true


    private val logoutPresenter: LoginOutPresenter by lazy {
        LoginOutPresenter()
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen)
//        startService(Intent(this, FloatingNotes::class.java))

        adminReceiver = ComponentName(this@MainActivity, ScreenOffAdminReceiver::class.java)

        refreshWifiStatus()

        if (null == savedInstanceState) {
            supportFragmentManager.beginTransaction().add(R.id.frameBody, HomeAppCenterFragment(), "appCenter").commit()
        }

        tvCompany.text = "${tvCompany.text} V${BuildConfig.VERSION_NAME}"

        var proc: Process? = null
        try {
            //proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "wm overscan -100,0,-40,0"))
            proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "wm overscan 0,0,0,0"))
            proc!!.waitFor()
        } catch (ex: Exception) {
            Log.i("TAG", "Could not reboot", ex)
        }
        logoutPresenter.attachView(this)
        timer = Timer()
        timerTask = object : TimerTask() {
            override fun run() {
                reconnectToServer()
            }
        }
        timer.schedule(timerTask, 1000, 5000)
    }

    fun showVideoDemo(view: View) {
        startActivity(intentFor<VideoActivity>())
    }

    /**
     * 无网络时弹窗
     */
    private fun isNetWorkDialog(): Boolean {
        //App无网络进入弹框
        if (!isNetworkConnected(this)) {
            //initHiddenMainDialog(this)
            return false
        }
        return false
    }

    private fun refreshWifiStatus() {
        if (isDestroyed || null == tvWifiName) return

        val wifiManager = applicationContext?.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (wifiManager.isWifiEnabled) {
            tvWifiName.text = "WiFi：${wifiManager.connectionInfo.ssid.replace("\"", "")}"
        } else {
            tvWifiName.text = "WiFi：未连接"
        }
        tvWifiName.postDelayed({
            refreshWifiStatus()
        }, 10000)

    }

    fun onClickSystemConfig(view: View) {
        if (isNetWorkDialog()) return
        SystemConfigDialog().show(fragmentManager, "config")
    }

    fun onClickWifiConfig(view: View) {
        val cachedFragment = supportFragmentManager.findFragmentByTag("wifiFragment")
        if (cachedFragment == null) {
            supportFragmentManager.beginTransaction().add(R.id.frameWifiList, WifiListFragment(), "wifiFragment").commit()
        } else {
            supportFragmentManager.beginTransaction().remove(cachedFragment).commit()
        }
    }

    fun onClickAppConfig(view: View) {
       // if (isNetWorkDialog()) return
        AppManagerActivity.launch(this)
    }

    override fun onMessageArrived(topic: String?, message: MqttMessage?) {
//        ToastGlobal.showToast("onMessageArrived:$topic")
        Log.d("onMessageArrived", "msg:" + message.toString())
//        tvMQTT.text = "${tvMQTT.text}\nonMessageArrived:${message.toString()}"

        MQTTHelper.processMessage(topic, message.toString(), this@MainActivity)

    }

    override fun onConnectionLost() {
        Log.d("MQTT", "onConnectionLost")
//        tvMQTT.text = "${tvMQTT.text}\nonConnectionLost"
//        ToastGlobal.showToast("连接 MQTT LOST：5秒后重试")
//        doAsync {
//            Thread.sleep(5000)
//            runOnUiThread {
//                connectToServer()
//            }
//        }
    }

    override fun onConnectionSuccess() {
        Log.d("MQTT", "onConnectionSuccess")
//        ToastGlobal.showToast("onConnectionSuccess")
//        tvMQTT.text = "${tvMQTT.text}\nonConnectionSuccess"

//        UserInfoInstance.instance.observe(this, Observer<ResponseLogin> {
//            if (!UserInfoInstance.instance.isGuestUser) {
//                subscribeToTopic(UserInfoInstance.instance.userInfo.profile.ID)
//            }
//        });
    }

    override fun onConnectionFailure() {
        Log.d("MQTT", "onConnectionFailure")
//        ToastGlobal.showToast("onConnectionFailure")
//        tvMQTT.text = "${tvMQTT.text}\nonConnectionFailure"

//        ToastGlobal.showToast("连接MQTT失败：5秒后重试")
//        doAsync {
//            Thread.sleep(5000)
//            runOnUiThread {
//                connectToServer()
//            }
//        }
    }


    override fun onDeliveryComplete() {
//        ToastGlobal.showToast("onDeliveryComplete")
//        tvMQTT.text = "${tvMQTT.text}\nonDeliveryComplete"
    }

    fun turnOffScreen() {
        val policyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        val admin = policyManager.isAdminActive(adminReceiver)
        if (admin) {
            policyManager.lockNow()
        } else {
            ToastGlobal.showToast("没有设备管理权限")
        }
    }


    @SuppressLint("InvalidWakeLockTag")
    fun turnOnScreen() {
        // turn on screen
        val mPowerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "tag")
        mWakeLock.acquire()
        mWakeLock.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer.cancel()
        timerTask.cancel()
    }

    fun logout() {
        logoutPresenter.loginout()
    }

    fun downloadFile(url: String) {
        showLoadingDialog("正在下载文件请稍等")
        val fileName = url.substring(url.lastIndexOf("/") + 1, url.length)

        doAsync {
            var file: File? = null
            try {
                val inputStream = BufferedInputStream(URL(url).openStream(), StreamUtils.IO_BUFFER_SIZE)
                val downloadPathParent = File(Environment.getExternalStorageDirectory(), "DCIM")
                if (!downloadPathParent .exists()) downloadPathParent .mkdirs()
                val downloadPath =File(downloadPathParent,"接收文件")
                if (!downloadPath.exists()) downloadPath.mkdirs()
//                val inputStream = BufferedInputStream(URL("http://foooooot.com/media/upload/selected_route/%E6%A1%83%E8%8A%B1%E5%B2%9B.jpg").openStream(), IO_BUFFER_SIZE)
                file = File(downloadPath, fileName)
                val outputStream = FileOutputStream(file, false)
                val out = BufferedOutputStream(outputStream, StreamUtils.IO_BUFFER_SIZE)
                StreamUtils.copy(inputStream, out)
                out.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            uiThread {
                dismissLoadingDialog()
                if (file != null) {
                    ToastGlobal.showToast("下载成功$fileName")
                } else {
                    ToastGlobal.showToast("下载失败")
                }
            }
        }
    }


    override fun loginOutSuccess(data: String) {
    }

    override fun loginOutFail() {
    }

    override fun showLoading() {
    }

    override fun hideLoading() {
    }

    override fun showError(errorMsg: String) {
    }

}
