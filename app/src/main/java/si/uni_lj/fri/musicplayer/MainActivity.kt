package si.uni_lj.fri.musicplayer


import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import si.uni_lj.fri.musicplayer.databinding.ActivityMainBinding
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate()")

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.startServiceButton.setOnClickListener {
            Toast.makeText(
                applicationContext,
                "Start service button will be used in the service implementation.",
                Toast.LENGTH_SHORT
            ).show()
        }
        binding.stopServiceButton.setOnClickListener {
            Toast.makeText(
                applicationContext,
                "Stop service button will be used in the service implementation.",
                Toast.LENGTH_SHORT
            ).show()
        }
        binding.aboutButton.setOnClickListener {
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
        player = MediaPlayer()
        Log.i(TAG, "onStart()")
    }

    /**
     * Starts the music player playback
     */
    fun play() {
        player?.let {
            if (it.isPlaying) {
                return
            }
            getFiles().random().apply {
                try {
                    val descriptor = assets.openFd(this)
                    it.setDataSource(
                        descriptor.fileDescriptor,
                        descriptor.startOffset,
                        descriptor.length
                    )
                    descriptor.close()
                    it.prepare()
                } catch (e: IOException) {
                    Log.w(TAG, "Could not open file", e)
                    return
                }
                it.isLooping = true
                it.start()

                // display song info
                binding.musicInfoTextView.text = this
                Log.i(TAG, "Playing song $this")
            }
        }
    }

    /**
     * Stops the music player playback
     */
    fun stop() {
        player?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.reset()
        }

        // display song info
        binding.musicInfoTextView.text = ""
    }

    /**
     * Returns the list of mp3 files in the assets folder
     *
     * @return
     */
    private fun getFiles(): List<String> =
        assets.list("")?.filter { it.toLowerCase(Locale.getDefault()).endsWith("mp3") }
            ?: emptyList()

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}