package com.gratiavalencia.queritany;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Piloto 126.25 · 📱 Recibe el enlace del widget desde Querit any (queritany://widget?c=<acceso>-<llave>),
 * lo guarda en el móvil, pone el widget al día y, si el móvil lo deja, ofrece ponerlo en la pantalla de inicio.
 */
public class WidgetConectar extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri u = getIntent() != null ? getIntent().getData() : null;
        String clave = u != null ? u.getQueryParameter("c") : null;
        if (WidgetDatos.guardarClave(this, clave)) {
            sendBroadcast(new Intent(this, QueritWidget.class).setAction(QueritWidget.REFRESCAR));
            AppWidgetManager m = AppWidgetManager.getInstance(this);
            ComponentName w = new ComponentName(this, QueritWidget.class);
            if (m.getAppWidgetIds(w).length == 0 && Build.VERSION.SDK_INT >= 26 && m.isRequestPinAppWidgetSupported()) {
                m.requestPinAppWidget(w, null, null);
                Toast.makeText(this, "💌 Widget conectado. Acepta para ponerlo en tu pantalla de inicio", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "💌 Widget conectado. Para ponerlo: mantén pulsado en la pantalla de inicio → Widgets → Querit any", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Ese enlace no vale. Vuelve a probar desde Querit any → 💾 guardar", Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
