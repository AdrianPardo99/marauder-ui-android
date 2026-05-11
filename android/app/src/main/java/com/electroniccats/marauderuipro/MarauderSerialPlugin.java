package com.electroniccats.marauderuipro;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.List;
import java.util.Map;

@CapacitorPlugin(name = "MarauderSerial")
public class MarauderSerialPlugin extends Plugin implements SerialListener {
    private static final String TAG = "MarauderSerial";
    private static final String ACTION_USB_PERMISSION = "com.electroniccats.marauderuipro.USB_PERMISSION";
    
    private SerialService service;
    private boolean bound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((SerialService.SerialBinder) binder).getService();
            service.attach(MarauderSerialPlugin.this);
            bound = true;
            Log.d(TAG, "Plugin bound to SerialService");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            bound = false;
        }
    };

    @Override
    public void load() {
        Log.d(TAG, "Loading MarauderSerialPlugin...");
        Intent intent = new Intent(getContext(), SerialService.class);
        getContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void handleOnDestroy() {
        if (bound) {
            if (service != null) service.detach();
            getContext().unbindService(serviceConnection);
            bound = false;
        }
        super.handleOnDestroy();
    }

    @PluginMethod
    public void connect(PluginCall call) {
        if (service == null) {
            call.reject("SerialService not ready. Wait a second and try again.");
            return;
        }

        // Ejecutar en hilo de fondo para evitar el crash del hilo principal
        new Thread(() -> {
            try {
                UsbManager manager = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
                Map<String, UsbDevice> deviceList = manager.getDeviceList();
                
                if (deviceList.isEmpty()) {
                    call.reject("No USB devices detected. Check your OTG cable.");
                    return;
                }

                List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
                if (availableDrivers.isEmpty()) {
                    call.reject("USB device found but no driver matches it.");
                    return;
                }

                UsbSerialDriver driver = availableDrivers.get(0);
                UsbDevice device = driver.getDevice();

                if (!manager.hasPermission(device)) {
                    // Fix for Android 14 (API 34+) security requirements
                    int flags = PendingIntent.FLAG_IMMUTABLE;
                    
                    Intent intent = new Intent(ACTION_USB_PERMISSION);
                    // Making the intent explicit for better security and compliance with API 34+
                    intent.setPackage(getContext().getPackageName());
                    
                    PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getContext(), 0, intent, flags);
                    manager.requestPermission(device, usbPermissionIntent);
                    call.reject("Permission required. Grant it and press Connect again.");
                    return;
                }

                // Intentar conectar vía el servicio
                service.connect(0);
                Log.i(TAG, "Connection initiated for " + device.getDeviceName());
                call.resolve();

            } catch (Exception e) {
                Log.e(TAG, "Connection error", e);
                call.reject("Crash prevented: " + e.getMessage());
            }
        }).start();
    }

    @PluginMethod
    public void send(PluginCall call) {
        String data = call.getString("data");
        if (service == null || service.getConnected() != SerialService.Connected.True) {
            call.reject("Not connected");
            return;
        }

        new Thread(() -> {
            try {
                service.write((data + "\n").getBytes());
                call.resolve();
            } catch (Exception e) {
                call.reject("Write error: " + e.getMessage());
            }
        }).start();
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        if (service != null) {
            service.disconnect();
        }
        call.resolve();
    }

    @Override
    public void onSerialConnect() {
        notifyListeners("connected", new JSObject());
    }

    @Override
    public void onSerialConnectError(Exception e) {
        JSObject ret = new JSObject();
        ret.put("error", e.getMessage());
        notifyListeners("error", ret);
    }

    @Override
    public void onSerialRead(byte[] data) {
        JSObject ret = new JSObject();
        ret.put("data", new String(data));
        notifyListeners("data", ret);
    }

    @Override
    public void onSerialIoError(Exception e) {
        JSObject ret = new JSObject();
        ret.put("error", e.getMessage());
        notifyListeners("error", ret);
    }
}
