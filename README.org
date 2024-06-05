#+OPTIONS: broken-links:t
* Tetramax Android workshop
** Introduction 
This document gives a step-by-step instructions on implementing a simple music player as an Android service.
** Slides
Accompanying slides can be found [[slides/Tetramax_AndroidBasics.pdf][here.]]

** Assignments
*** Start and stop playback when clicking the =PLAY= and =STOP= buttons
Let's start (and stop) the playback when the user clicks on the appropriate buttons.

  - In =MainActivity=, define member variables =playButton= and =stopButton= (of class =Button=, similar to =startServiceButton= and =stopServiceButton=).
  - In =MainActivity.onCreate()=, link =playButton= and =stopButton= to the actual buttons defined in XML: use =findViewById()= to create the association.
  - Add behavior to these buttons by providing implementations of =View.OnClickListener=. These implementations should simply call functions =play()= and =stop()=. (Hint, look at the code that is used to add behavior to =startServiceButton= and =stopServiceButton=.)

    Notice that while the application works, it behaves unpredictably. (The playback stops when the users rotates the phone, closes the screen, or switches to another application; you may need to do these actions multiple times, before the playback stops, but it will stop eventually.)

*** Stop playback when the activity pauses
The reason for such behavior is that when you rotate the screen, your =MainActivity= instance gets destroyed (together with the =MediaPlayer= instance which stops the playback). While the playback may 'survive' few initial screen rotations (and other configuration changes), it will stop eventually. The proper way of handling this is to manually stop the playback whenever the activity configuration changes. To stop the playback:
    
  - Override the implementation of =MainActivity.onPause()=,
  - manually stop the playback by calling =stop()=,
  - and release the player instance by calling =player.release()=.

Now the playback should stop on all instances mentioned above.

*** Implement the music player as a service
However, we want the playback to continue even if the user switches to another applications, rotates or dims the screen (presses the power button). To that end, we are going to implement the player as a service.
**** Add a service
- Go to menu and click through =File > New > Service > Service=.
- As the name write =MusicService=.
- Remove the tick in the =Exported= option; we do not want that our music service is instantiated by other android applications.
**** Move the player code into service
Next, we will gradually migrate all player code into the service. Initially, the service will automatically play the music when started. We will add explicit controls later on. Make the following modifications:

- Add a constant member variable of type =MediaPlayer= to =MusicService= and immediately instantiate it: =val player = MediaPlayer()=
- Override method =MusicService.onDestroy= and inside release the =MediaPlayer= instance.
- Create companion object in =MusicService= with =TAG= field and change =TAG= references to point to =MusicService=
- Move =play()= and =stop()= methods from =MainActivity= to =MusicService=:
  - Also, comment out the code that changes =TextView= objects since that code cannot be used from the service.
  - Also in =MainActivity=, comment out all calls to methods =play= and =stop=.
- Move method =getFiles()= from =MainActivity= to =MusicService=.
- Override =MusicService.onStartCommand= by adding the following behavior:
  - the call to method =play()=;
  - the return of value =START_STICKY=.
- In =MainActivity=, remove all references to the =player= instance as well as all calls to =start()= and =stop()=.
- In =MainActivity.onCreate()= change the behavior of =startServiceButton= so that it starts the service instead of showing the Toast message. The service is started by making the =Intent= instance and calling the =startService= method as shown below.
#+BEGIN_SRC java
val intent = Intent(this@MainActivity, MusicService::class.java)
startService(intent)
#+END_SRC
- Similarly, in =MainActivity.onCreate= change the behavior of =stopServiceButton= so that it stops the service instead of showing the message. The service is stopped as shown below.
#+BEGIN_SRC java
stopService(Intent(this@MainActivity, MusicService::class.java))
#+END_SRC

If you now press the =startServiceButton=, notice how the playback starts and it does not stop if you rotate the screen, switch to another application, or dim the screen. The playback stops only if you stop the service with the =stopServiceButton= or remove the application from the application drawer.
*** Bind activity to the service
While running the playback in a service is an improvement, the current solution is a bit awkward. In particular, we have very little control over the service. For instance, the playback starts as the service is created, and the only way to stop it, is to stop the service. It would be much better if we could have more fine-grained control over playback. To that end, we will *bind* to the service from the activity. This requires making changes to both =MusicService= and =MainActivity=.
**** Make service return a =Binder= instance
When we bind to the service the Android system will asynchronously return a =Binder= instance. We'll create a type of a =Binder= implementation, named =LocalBinder=, that will hold a reference to the instance of =MusicService=.
***** Add class =LocalBinder=
Inside our =MusicService=, define the =LocalBinder= as a static inner class. Use the following code.
#+BEGIN_SRC java
// an implementation of Binder interface
internal class LocalBinder(val service: MusicService) : Binder()

// a reference to LocalBinder
private val binder = LocalBinder(this)
#+END_SRC
***** Implement =onBind= to return the service instance
Next, make =MusicService.onBind()= return the binder instance whenever we bind to the service.
#+BEGIN_SRC java
override fun onBind(intent: Intent?): IBinder = binder
#+END_SRC
***** Update the =MusicService.onStartCommand()=
Finally, to stop the automatic playback upon service creation, remove the call to =play()= inside =MusicService.onStartCommand()=.
*** Bind to the service in the =MainActivity=
The =MainActivity= now has to bind to the service whenever the service is running. To know whether a binding is active and to have a reference to the service, we'll begin by defining a reference to the =MusicService= instance.
**** Create a reference to the =MusicService= instance inside =MainActivity=
This instance will be used to determine whether the =MainActivity= is bounded to the service
#+BEGIN_SRC java
private var service: MusicService? = null
#+END_SRC
The =null= value should denote the absence of a binding.
**** Implement a =ServiceConnection= object that handles =onServiceConnected()= and =onServiceDisconnected()= events
When we bind to the service (from the =MainActivity=), we receive an asynchronous callback denoting whether the connection has been established. If the connection has been established, we can cast the =IBinder= instance into =MusicService.LocalBinder= and read the =service= member variable. Having this reference allows us to control the service with much finer granularity.
#+BEGIN_SRC java
private val connection: ServiceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        Log.i(TAG, "onServiceConnected()")
        this@MainActivity.service = (service as LocalBinder).service
    }

    override fun onServiceDisconnected(name: ComponentName) {
        Log.i(TAG, "onServiceDisconnected()")
        service = null
    }
}
#+END_SRC
**** Add behaviors to buttons: =play=, =stop=, =startService=, and =stopService=
Finally, we now have to change the behavior of the buttons that start and stop the service and start and stop the playback. Change the contents inside the =MainActivity.onCreate()= to contain the following snippets. Note how all service calls are guarded will =null= checks using the =?= operator; if the =service= is =null= certain actions should not be called because they would raise =NullPointerException=.
#+BEGIN_SRC java
playButton?.setOnClickListener { service?.play() }
stopButton?.setOnClickListener { service?.stop() }
startServiceButton?.setOnClickListener {
    val intent = Intent(this@MainActivity, MusicService::class.java)
    startService(intent)
    bindService(intent, connection, BIND_AUTO_CREATE)
}
stopServiceButton?.setOnClickListener {
    service?.let {
        unbindService(connection)
        service = null
        stopService(Intent(this@MainActivity, MusicService::class.java))
    }
}
#+END_SRC
To the start playback, first start the service and then press the play button. Now, you can stop the playback with stop and restart it by pressing the play again. Note that the service was not destroyed and created during this start/stop/start cycle, but you directly controlled the =MediaPlayer= instance by calling the =service.start()= and =service.stop()= methods.
       
However, there is still a small issue: if you start the playback (start the service and then the playback) and rotate the screen, the music will continue to play, however, the start and stop button will stop working. Moreover, if you look at the output of the =Logcat= system, you'll see a warning about a memory leak. Something similar to this:
#+BEGIN_SRC text
E/ActivityThread: Activity tetramax.android.MainActivity has leaked ServiceConnection tetramax.android.MainActivity$1@4f60225 that was originally bound here
android.app.ServiceConnectionLeaked: Activity tetramax.android.MainActivity has leaked ServiceConnection tetramax.android.MainActivity$1@4f60225 that was originally bound here
at android.app.LoadedApk$ServiceDispatcher.<init>(LoadedApk.java:1610)
at android.app.LoadedApk.getServiceDispatcher(LoadedApk.java:1502)
at android.app.ContextImpl.bindServiceCommon(ContextImpl.java:1659)
at android.app.ContextImpl.bindService(ContextImpl.java:1612)
at android.content.ContextWrapper.bindService(ContextWrapper.java:698)
at tetramax.android.MainActivity$4.onClick(MainActivity.java:69)
at android.view.View.performClick(View.java:6597)
at android.view.View.performClickInternal(View.java:6574)
at android.view.View.access$3100(View.java:778)
at android.view.View$PerformClick.run(View.java:25885)
at android.os.Handler.handleCallback(Handler.java:873)
at android.os.Handler.dispatchMessage(Handler.java:99)
at android.os.Looper.loop(Looper.java:193)
at android.app.ActivityThread.main(ActivityThread.java:6669)
at java.lang.reflect.Method.invoke(Native Method)
at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:493)
at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:858)
#+END_SRC
**** Unbind when activity gets destroyed and bind on start if the service is already running
The issue above is caused by an Android configuration change. When you rotate the screen, the activity and all of its member variables get destroyed and then recreated with their default values. So when you rotate the screen, the =service= instance in the newly created =MainActivity= is set to =null= and the binding to the service is lost; the existing button references point to object that no longer exist.

We'll fix this by manually (i) unbinding from service whenever the activity is stopped, and (ii) binding to the service whenever the activity is started (if the service is running). To unbind when the activity is stopped, override the =MainActivity.onStop()= method.
#+BEGIN_SRC java
override fun onStop() {
    Log.i(TAG, "onStop()")
    service?.let {
        unbindService(connection)
        service = null
    }
    super.onStop()
}
#+END_SRC
 
To bind to the service when the activity is started, override the =MainActivity.onStart()= and bind to the service, but only if the service is running. To find out whether the =MusicService= is running, use the method given below.
#+BEGIN_SRC java
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
}

/** Returns true iff the MusicService service is running */
@Suppress("DEPRECATION")
private fun isServiceRunning(): Boolean =
    (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        .getRunningServices(Int.MAX_VALUE)
        .any { it.service.className == MusicService::class.java.canonicalName }
#+END_SRC

Doing this fixes both the memory leak and the broken start and play buttons.

At this stage, we are only missing the communication between the service and the activity. Right now we can control the service from the activity (by calling the methods on the =service= instance), but there are cases when the service needs to send a message to the activity on itself. For instance, to tell the name of the song that is playing, or to signal how far the current song has been played.
*** Send the name of the song with a local broadcast
One way of sending messages between Android components is to use [[https://developer.android.com/guide/components/broadcasts.html][broadcasts.]] Since in our case all components are part of the same application, we shall use local broadcasts.
**** Set up a variable that holds the name of the song in service
First, we'll set-up a member variable inside =MusicService= that will hold the name of the song that is being played.
#+BEGIN_SRC java
// holds the name of the song currently being played
var song = ""
#+END_SRC
Then, change the implementations of =MusicService.play()= and =MusicService.stop()= to set the value of this member variable appropriately.

Inside =MusicService.play()= simply add line =song = this= after =it.start()=.

At the end of =MusicService.stop()= add line =this.song = " "= after =it.reset()=.
**** Set up a =LocalBroadcastReceiver= to send a message whenever a playback starts
Now, whenever the playback starts (or stops) we shall broadcast the name of the song with the help of a local broadcast. Let's define a helper method that sends a local broadcast message.
#+BEGIN_SRC java
private fun broadcastSongName() {
    val intent = Intent("mplayer") // mplayer is the name of the broadcast
    intent.putExtra("song", song) // song name is added as the parameter
    LocalBroadcastManager.getInstance(this).sendBroadcast(intent) // the broadcast is sent
}
#+END_SRC

To send a local broadcast when the song starts or stops, call the method above at the end of the =MusicService.play()= and =MusicService.stop()=:
#+BEGIN_SRC java
broadcastSongName()
#+END_SRC
**** Subscribe (and unsubscribe) to broadcasts in =MainActivity=
Finally, we have to subscribe to these broadcasts in =MainActivity=. But first we have to define what happens when a broadcast is received. We do this by implementing a =BroadcastReceiver=. Ad the following code inside the =MainActivity=.
#+BEGIN_SRC java
private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        musicInfoTextView?.text = intent?.getStringExtra("song")
    }
}
#+END_SRC
This code simply reads the =song= parameter from the =Intent= that came with the local broadcast and writes its value to the =musicInfoTextView=.
     
To avoid memory leaks we should be subscribed to broadcasts only when the activity is active. So it makes sense to subscribe in =MainActivity.onStart()= and unsubscribe in =MainActivity.onStop()=.
#+BEGIN_SRC java
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
#+END_SRC
Note that the parameter to the =IntentFilter= is the same string which is used in =MusicService= when sending the broadcast. And to unsubscribe when the activity is stopped, make the =MainActivity.onStop()= contain the following code.
#+BEGIN_SRC java
override fun onStop() {
    Log.i(TAG, "onStop()")
    service?.let {
        unbindService(connection)
        service = null
    }
    LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
    super.onStop()
}
#+END_SRC

Notice that now whenever you start the playback, the name of the song is displayed in the =TextView= in the middle of the screen. When the playback is stopped, the song name disappears.

However, there are a few bugs left. For instance, if you start the playback and rotate the screen, the song name goes away. Or if you start the playback and terminate the service directly by pressing the stop service button, the song name remains. Fixing these two bugs is left as an exercise on your own.
** Expansion ideas
The purpose of this workshop was to demonstrate the use of a few Android components, namely activities, services, intents and broadcasts. To that end, the code was kept simple. To make this music player more practical, you may, on your own, try to implement a few additional features. For instance:
- Display the playback progress: show the amount of time the song has been playing. One way of implementing this is to add a =Timer= instance to =MusicService= that, at regular intervals, sends broadcasts containing information about the current playback.
- Implement the service as a [[https://developer.android.com/guide/components/services#Foreground][foreground service.]] A foreground services shows a notification and quick controls that allow user actions. For instance, controls for stopping and starting the playback.
** Attribution
All MP3 songs used in this project were obtained from [[https://www.bensound.com/royalty-free-music][bensound.com]] under creative commons license.
