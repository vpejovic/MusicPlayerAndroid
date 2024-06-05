package si.uni_lj.fri.musicplayer


import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.media.MediaPlayer
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {
    private var musicInfoTextView: TextView? = null
    private var startServiceButton: Button? = null
    private var stopServiceButton: Button? = null
    private var aboutButton: Button? = null
    private var playButton: Button? = null
    private var stopButton: Button? = null

    private var service: MusicService? = null

    private val connection: ServiceConnection = object: ServiceConnection{
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "onServiceConnected()")
            service = (binder as MusicService.LocalBinder).service
            musicInfoTextView?.text = service?.song
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(TAG, "onServiceDisconnected()")
            service = null
        }
    }

    private val receiver:BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            musicInfoTextView?.text = intent?.getStringExtra("song")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate()")

        setContentView(R.layout.activity_main)

        musicInfoTextView = findViewById(R.id.musicInfoTextView)
        startServiceButton = findViewById(R.id.startServiceButton)
        stopServiceButton = findViewById(R.id.stopServiceButton)
        playButton = findViewById(R.id.playButton)
        stopButton = findViewById(R.id.stopButton)
        aboutButton = findViewById(R.id.aboutButton)

        playButton?.setOnClickListener {
            service?.play()
        }

        stopButton?.setOnClickListener {
            service?.stop()
        }

        startServiceButton?.setOnClickListener {
            val intent = Intent(this, MusicService::class.java)
            startService(intent)
            bindService(intent, connection, BIND_AUTO_CREATE)
        }

        stopServiceButton?.setOnClickListener {
            unbindService(connection)
            service = null
            val intent = Intent(this, MusicService::class.java)
            stopService(intent)
        }

        aboutButton?.setOnClickListener {
            startActivity(
                Intent(
                    this@MainActivity,
                    AboutActivity::class.java
                )
            )
        }
    }

    override fun onStart() {
        super.onStart()

        if (isServiceRunning()) {
            bindService(Intent(this, MusicService::class.java),
                connection,
                BIND_AUTO_CREATE)
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            receiver,
            IntentFilter("mPlayer")
        )

        Log.i(TAG, "onStart()")
    }

    override fun onPause() {
        service?.let {
            unbindService(connection)
            service = null
        }
        super.onPause()
        Log.i(TAG, "onPause()")
    }

    override fun onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        super.onStop()
        Log.i(TAG, "onStop()")
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean =
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == MusicService::class.java.canonicalName }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}