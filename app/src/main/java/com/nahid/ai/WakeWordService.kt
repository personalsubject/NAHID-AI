package com.nahid.ai

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.*
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.speech.*
import java.util.Locale

class WakeWordService : Service() {

    private val channelId = "nahid_ai_channel"
    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())

    private var awaitingCommand = false
    private var listening = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())
        startRecognition()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (intent?.action == "STOP_NAHID_AI") {
            stopSelf()
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "NAHID AI Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, WakeWordService::class.java).apply {
            action = "STOP_NAHID_AI"
        }

        val pendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, channelId)
            .setContentTitle("NAHID AI চালু আছে")
            .setContentText("ভয়েস কমান্ড শোনার জন্য প্রস্তুত")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .addAction(
                android.R.drawable.ic_media_pause,
                "বন্ধ করো",
                pendingIntent
            )
            .build()
    }

    private fun startRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

            speechRecognizer?.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {
                        listening = true
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        listening = false
                    }

                    override fun onError(error: Int) {
                        listening = false
                        scheduleRestart()
                    }

                    override fun onResults(results: Bundle?) {
                        listening = false

                        val matches = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                        val phrase = matches?.firstOrNull()?.lowercase(
                            Locale.forLanguageTag("bn-BD")
                        ).orEmpty()

                        processPhrase(phrase)
                        scheduleRestart()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                }
            )
        }

        if (!listening) {
            try {
                val intent = Intent(
                    RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                ).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE,
                        "bn-BD"
                    )
                    putExtra(
                        RecognizerIntent.EXTRA_MAX_RESULTS,
                        3
                    )
                }

                speechRecognizer?.startListening(intent)
                listening = true
            } catch (_: Exception) {
                scheduleRestart()
            }
        }
    }

    private fun scheduleRestart() {
        handler.postDelayed({
            listening = false
            startRecognition()
        }, 1000)
    }

    private fun processPhrase(phrase: String) {
        if (phrase.isBlank()) return

        val wakeWords = listOf("hey nahid", "হেই নাহিদ", "হে নাহিদ")

        val foundWakeWord = wakeWords.any { phrase.contains(it) }

        if (foundWakeWord) {
            val command = wakeWords.fold(phrase) { text, word ->
                text.replace(word, "").trim()
            }

            if (command.isBlank()) {
                awaitingCommand = true
                return
            }

            awaitingCommand = false
            runCommand(command)
            return
        }

        if (awaitingCommand) {
            awaitingCommand = false
            runCommand(phrase)
        }
    }

    private fun runCommand(command: String) {
        when {
            command.contains("ইউটিউব") || command.contains("youtube") -> {
                val query = command
                    .replace("ইউটিউবে", "")
                    .replace("ইউটিউব", "")
                    .replace("youtube", "")
                    .trim()

                val url = if (query.isBlank()) {
                    "https://www.youtube.com"
                } else {
                    "https://www.youtube.com/results?search_query=" +
                        Uri.encode(query)
                }

                openUrl(url)
            }

            command.contains("অ্যালার্ম") ||
                command.contains("alarm") -> {
                setAlarm(command)
            }

            command.contains("কল") ||
                command.contains("call") -> {
                val name = command
                    .replace("আমাকে", "")
                    .replace("কল করো", "")
                    .replace("কল দাও", "")
                    .replace("কল", "")
                    .replace("call", "")
                    .trim()

                if (name.isNotBlank()) {
                    callContact(name)
                }
            }

            command.contains("ব্রাউজার") ||
                command.contains("ইন্টারনেট") -> {
                openUrl("https://www.google.com")
            }
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun setAlarm(command: String) {
        val number = Regex("\\d{1,2}").find(command)?.value?.toIntOrNull()
            ?: return

        val hour = number.coerceIn(0, 23)
        val minute = 0

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "NAHID AI Alarm")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun callContact(name: String) {
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        if (checkSelfPermission(android.Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )

        val selection =
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"

        val args = arrayOf("%$name%")

        val numbers = mutableSetOf<String>()

        val cursor: Cursor? = contentResolver.query(
            uri,
            projection,
            selection,
            args,
            null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val number = it.getString(0)
                if (!number.isNullOrBlank()) {
                    numbers.add(number)
                }
            }
        }

        if (numbers.size == 1) {
            try {
                val callIntent = Intent(
                    Intent.ACTION_CALL,
                    Uri.parse("tel:${numbers.first()}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                startActivity(callIntent)
            } catch (_: Exception) {
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
