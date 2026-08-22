package com.lucerion.launcher.motor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Servicio en primer plano mientras el juego corre: sube la prioridad del
 * proceso para que Android no lo mate al minimizar (la JVM del juego vive en
 * este mismo proceso). Sin esto, salir un momento de la app suele terminar
 * con el juego cerrado por el recolector de memoria del sistema.
 */
class ServicioJuego : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val canal = NotificationChannel(
            "lucerion_juego",
            "Juego en ejecución",
            NotificationManager.IMPORTANCE_LOW,
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(canal)
        val notificacion: Notification = NotificationCompat.Builder(this, "lucerion_juego")
            .setContentTitle("Cretania en ejecución")
            .setContentText("La partida sigue viva mientras la app está en segundo plano.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
        startForeground(2, notificacion)
        return START_NOT_STICKY
    }
}
