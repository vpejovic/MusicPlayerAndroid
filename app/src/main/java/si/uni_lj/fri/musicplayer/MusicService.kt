package si.uni_lj.fri.musicplayer

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.IOException
import java.util.Locale

class MusicService : Service() {

    companion object {
        private val TAG = MusicService::class.java.simpleName
    }

    val player = MediaPlayer()

    var song = ""

    internal class LocalBinder(val service: MusicService) : Binder()

    private val binder = LocalBinder(this)

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        player.release()
        Log.i(TAG, "onDestroy")
        super.onDestroy()
    }

    /**
     * Starts the music player playback
     */
    fun play() {
        if (player.isPlaying) {
            return
        }
        getFiles().random().apply {
            try {
                val descriptor = assets.openFd(this)
                player.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length
                )
                descriptor.close()
                player.prepare()
            } catch (e: IOException) {
                Log.w(TAG, "Could not open file", e)
                return
            }
            player.isLooping = true
            player.start()

            // display song info
            //musicInfoTextView?.text = this
            song = this
            broadcastSongName()
            Log.i(TAG, "Playing song $this")
        }
    }

    /**
     * Stops the music player playback
     */
    fun stop() {
        if (player.isPlaying) {
            player.stop()
        }
        player.reset()

        // display song info
        song = ""
        broadcastSongName()
        //musicInfoTextView?.text = ""
    }

    private fun broadcastSongName() {
        val intent = Intent("mPlayer")
        intent.putExtra("song", this.song)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    /**
     * Returns the list of mp3 files in the assets folder
     *
     * @return
     */
    private fun getFiles(): List<String> =
        assets.list("")?.filter { it.lowercase(Locale.getDefault()).endsWith("mp3") }
            ?: emptyList()

}