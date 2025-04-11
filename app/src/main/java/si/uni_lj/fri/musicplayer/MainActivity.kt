package si.uni_lj.fri.musicplayer


import android.Manifest
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import si.uni_lj.fri.musicplayer.databinding.ActivityMainBinding
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        const val NOTIF_REQUEST_CODE = 42
    }

    private lateinit var binding: ActivityMainBinding

    private var service: MusicService? = null

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.i(TAG, "onServiceConnected()")
            this@MainActivity.service = (service as MusicService.LocalBinder).service

            binding.musicInfoTextView.text = this@MainActivity.service?.song
            handler.post(updateTimeRunnable) // Start timer
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.i(TAG, "onServiceDisconnected()")
            service = null
        }
    }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            binding.musicInfoTextView.text = intent?.getStringExtra("song")
        }
    }

    private val handler = android.os.Handler(Looper.getMainLooper())

    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            service?.player?.let {
                if (it.isPlaying) {
                    val elapsed = it.currentPosition / 1000
                    val minutes = elapsed / 60
                    val seconds = elapsed % 60
                    binding.songProgressTextView.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun serviceStarting(){
        val intent = Intent(this@MainActivity, MusicService::class.java)
        startForegroundService(intent)
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    private fun makeRequest() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIF_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission was denied")
        } else {
            Log.d(TAG, "Permission was granted")
            serviceStarting()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate()")

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.startServiceButton.setOnClickListener {

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "Permission to post notification denied")

                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)){

                    val builder = AlertDialog.Builder(this)
                    with(builder){
                        setMessage("If you don't enable this, you won't receive up-to-date info from this app!")
                        setTitle("Permission I really need")
                        setPositiveButton("OK"){p0, p1->
                            makeRequest()
                        }
                    }

                    val dialog = builder.create()
                    dialog.show()

                } else {
                    makeRequest()
                }

            } else {
                serviceStarting()
            }

        }
        binding.stopServiceButton.setOnClickListener {
            Log.d(TAG, "stopServiceButton clicked")
            service?.let {
                unbindService(connection)
                service = null
                stopService(Intent(this@MainActivity, MusicService::class.java))
                binding.musicInfoTextView.text = "" // Clear song name from UI
                binding.songProgressTextView.text = "00:00"
                handler.removeCallbacks(updateTimeRunnable)
            }
        }
        binding.aboutButton.setOnClickListener {
            startActivity(
                Intent(
                    this@MainActivity,
                    AboutActivity::class.java
                )
            )
        }

        binding.playButton.setOnClickListener { service?.play()}
        binding.stopButton.setOnClickListener { service?.stop()}
    }

    override fun onStop() {
        handler.removeCallbacks(updateTimeRunnable)
        service?.let {
            unbindService(connection)
            service = null
        }

        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        Log.i(TAG, "onStart()")
        if (isServiceRunning()) {
            bindService(
                Intent(this@MainActivity, MusicService::class.java),
                connection,
                BIND_AUTO_CREATE
            )
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, IntentFilter("mplayer"))
    }


    /** Returns true iff the MusicService service is running */
    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean =
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == MusicService::class.java.canonicalName }


}