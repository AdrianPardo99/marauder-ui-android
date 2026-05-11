package com.electroniccats.marauderuipro;

import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;

public class SerialSocket implements SerialInputOutputManager.Listener {

    private static final int BAUD_RATE = 115200;

    private final Context context;
    private SerialListener listener;
    private UsbDeviceConnection connection;
    private UsbSerialPort port;
    private SerialInputOutputManager ioManager;

    public SerialSocket(Context context, UsbDeviceConnection connection, UsbSerialPort port) {
        this.context = context.getApplicationContext();
        this.connection = connection;
        this.port = port;
    }

    public void connect(SerialListener listener) throws Exception {
        this.listener = listener;

        port.open(connection);
        port.setParameters(
            BAUD_RATE,
            UsbSerialPort.DATABITS_8,
            UsbSerialPort.STOPBITS_1,
            UsbSerialPort.PARITY_NONE
        );

        ioManager = new SerialInputOutputManager(port, this);
        ioManager.start();

        listener.onSerialConnect();
    }

    public void write(byte[] data) throws IOException {
        if (port == null) throw new IOException("Puerto no conectado");
        port.write(data, 2000);
    }

    public void disconnect() {
        listener = null;
        if (ioManager != null) {
            ioManager.setListener(null);
            ioManager.stop();
            ioManager = null;
        }
        if (port != null) {
            try { port.close(); } catch (Exception ignored) {}
            port = null;
        }
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    @Override
    public void onNewData(byte[] data) {
        if (listener != null) listener.onSerialRead(data);
    }

    @Override
    public void onRunError(Exception e) {
        if (listener != null) listener.onSerialIoError(e);
    }
}
