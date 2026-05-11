package com.electroniccats.marauderuipro;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Método más robusto para Capacitor 6
        registerPlugin(MarauderSerialPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
