package com.electroniccats.marauderuipro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.List;

public class SerialService extends Service implements SerialListener {

    public enum Connected { False, Pending, True }

    private final Handler mainLooper = new Handler(Looper.getMainLooper());
    private final IBinder binder = new SerialBinder();

    private final ArrayDeque<byte[]> queue1 = new ArrayDeque<>();
    private final ArrayDeque<byte[]> queue2 = new ArrayDeque<>();

    private SerialSocket socket;
    private SerialListener listener;
    private Connected connected = Connected.False;

    private static final String NOTIFICATION_CHANNEL = "marauder_serial";
    private static final int NOTIFICATION_ID = 1;

    public class SerialBinder extends Binder {
        public SerialService getService() { return SerialService.this; }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onDestroy() {
        cancelNotification();
        disconnect();
        super.onDestroy();
    }

    public void attach(SerialListener listener) {
        if (Looper.getMainLooper().getThread() != Thread.currentThread())
            throw new IllegalArgumentException("No estás en el main thread");

        cancelNotification();
        this.listener = listener;

        if (!queue1.isEmpty() || !queue2.isEmpty()) {
            for (byte[] data : queue1) listener.onSerialRead(data);
            for (byte[] data : queue2) listener.onSerialRead(data);
            queue1.clear();
            queue2.clear();
        }
    }

    public void detach() {
        if (connected.equals(Connected.True)) createNotification();
        listener = null;
    }

    public void connect(int portNum) throws Exception {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);

        if (availableDrivers.isEmpty()) throw new IOException("No se encontró ningún dispositivo USB serial");

        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDevice device = driver.getDevice();

        if (!usbManager.hasPermission(device)) {
            throw new SecurityException("Sin permiso USB");
        }

        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) throw new IOException("No se pudo abrir el dispositivo USB");

        List<UsbSerialPort> ports = driver.getPorts();
        if (ports.isEmpty()) throw new IOException("El dispositivo no tiene puertos disponibles");
        
        UsbSerialPort port = ports.get(Math.min(portNum, ports.size() - 1));
        socket = new SerialSocket(getApplicationContext(), connection, port);
        connected = Connected.Pending;
        socket.connect(this);
    }

    public void disconnect() {
        connected = Connected.False;
        if (socket != null) {
            socket.disconnect();
            socket = null;
        }
    }

    public void write(byte[] data) throws IOException {
        if (!connected.equals(Connected.True)) throw new IOException("No conectado");
        socket.write(data);
    }

    public Connected getConnected() { return connected; }

    @Override
    public void onSerialConnect() {
        connected = Connected.True;
        mainLooper.post(() -> {
            if (listener != null) {
                listener.onSerialConnect();
            }
        });
    }

    @Override
    public void onSerialConnectError(Exception e) {
        connected = Connected.False;
        mainLooper.post(() -> {
            if (listener != null) {
                listener.onSerialConnectError(e);
            } else {
                queue1.add(("Error de conexión: " + e.getMessage() + "\n").getBytes());
            }
        });
    }

    @Override
    public void onSerialRead(byte[] data) {
        mainLooper.post(() -> {
            if (listener != null) {
                listener.onSerialRead(data);
            } else {
                queue2.add(data);
            }
        });
    }

    @Override
    public void onSerialIoError(Exception e) {
        connected = Connected.False;
        mainLooper.post(() -> {
            if (listener != null) {
                listener.onSerialIoError(e);
            }
        });
    }

    private void createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL,
                "Conexión Marauder",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);

        Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Marauder conectado")
                .setContentText("Conexión serial activa en background")
                .setContentIntent(pendingIntent)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private void cancelNotification() {
        stopForeground(true);
    }
}
