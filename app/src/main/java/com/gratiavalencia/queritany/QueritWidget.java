package com.gratiavalencia.queritany;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Piloto 126.25 · 📱 El widget de Querit any en la pantalla de inicio: los planes de los próximos días
 * (los tuyos y los de tus calendarios compartidos). Se pone al día cada 30 minutos, al tocar ↻ y al conectarlo.
 * Al tocarlo se abre Querit any. Ver WidgetDatos.
 * (1.3: hay también la lista grande, QueritWidgetGrande, y el calendario del mes, QueritWidgetMes. Al ponerse
 * al día, se pintan los tres.)
 */
public class QueritWidget extends AppWidgetProvider {
    static final String REFRESCAR = "com.gratiavalencia.queritany.WIDGET_REFRESCAR";
    static final int MAX_FILAS = 7;

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        pintar(c, m, ids, MAX_FILAS);
        refrescar(this, c);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        if (REFRESCAR.equals(i.getAction())) refrescar(this, c);
    }

    /** Pide los datos en segundo plano y vuelve a pintar todos los widgets. */
    static void refrescar(BroadcastReceiver r, final Context c) {
        final BroadcastReceiver.PendingResult pr = r.goAsync();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    WidgetDatos.actualizar(c);
                    WidgetDatos.pintarTodos(c);
                } finally {
                    pr.finish();
                }
            }
        }).start();
    }

    static int flags() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
    }

    /** El aviso que sale en lugar de los planes (sin conectar, desconectado…), o null si todo va bien. */
    static String aviso(SharedPreferences p) {
        String estado = p.getString("estado", null);
        if (p.getString("acceso", null) == null) return "Para verlo, abre Querit any → 💾 guardar → 📱 conectar el widget ✦";
        if ("desconectado".equals(estado)) return "Este widget se ha desconectado. Vuelve a conectarlo en Querit any → 💾 guardar.";
        if ("llave".equals(estado)) return "Tu llave ha cambiado. Vuelve a conectar el widget en Querit any → 💾 guardar.";
        if ("sinred".equals(estado) || "conectando".equals(estado)) return "Buscando tus planes… ✦";
        return null;
    }

    /** Tocar el widget abre Querit any; tocar ↻ lo pone al día. */
    static void botones(Context c, RemoteViews v) {
        v.setOnClickPendingIntent(R.id.raiz, PendingIntent.getActivity(c, 0, new Intent(c, LauncherActivity.class), flags()));
        v.setOnClickPendingIntent(R.id.refrescar, PendingIntent.getBroadcast(c, 1, new Intent(c, QueritWidget.class).setAction(REFRESCAR), flags()));
    }

    /** La lista de planes (la normal con 7 filas, la grande con más). */
    static void pintar(Context c, AppWidgetManager m, int[] ids, int maxFilas) {
        if (ids == null || ids.length == 0) return;
        SharedPreferences p = WidgetDatos.prefs(c);
        String pkg = c.getPackageName();
        RemoteViews v = new RemoteViews(pkg, R.layout.widget_querit);
        String hoy = WidgetDatos.hoyAqui();
        v.setTextViewText(R.id.fecha, WidgetDatos.fechaLarga(hoy));
        v.removeAllViews(R.id.lista);

        String aviso = aviso(p);
        int filas = 0;
        if (aviso == null) {
            try {
                JSONArray dias = new JSONArray(p.getString("dias", "[]"));
                for (int i = 0; i < dias.length() && filas < maxFilas; i++) {
                    JSONObject d = dias.getJSONObject(i);
                    String k = d.getString("k");
                    if (k.compareTo(hoy) < 0) continue; // (lo guardado de ayer, si aún no ha llegado lo nuevo)
                    long dif = WidgetDatos.diaN(k) - WidgetDatos.diaN(hoy);
                    String nombre = dif == 0 ? "hoy" : dif == 1 ? "mañana" : WidgetDatos.SEMANA[WidgetDatos.diaSemana(k)] + " " + Integer.parseInt(k.substring(8));
                    RemoteViews cab = new RemoteViews(pkg, R.layout.widget_dia);
                    cab.setTextViewText(R.id.texto, nombre);
                    v.addView(R.id.lista, cab);
                    JSONArray planes = d.getJSONArray("planes");
                    for (int j = 0; j < planes.length() && filas < maxFilas; j++) {
                        JSONObject pl = planes.getJSONObject(j);
                        RemoteViews f = new RemoteViews(pkg, R.layout.widget_plan);
                        f.setInt(R.id.barra, "setBackgroundColor", Color.parseColor(pl.optString("c", "#e2622c")));
                        f.setTextViewText(R.id.hora, pl.optString("h", ""));
                        f.setTextViewText(R.id.titulo, pl.optString("t", "plan"));
                        v.addView(R.id.lista, f);
                        filas++;
                    }
                }
            } catch (Exception e) {
                aviso = "Buscando tus planes… ✦";
            }
            if (aviso == null && filas == 0) aviso = "No tienes planes en los próximos días ✦";
        }
        v.setViewVisibility(R.id.vacio, aviso != null ? View.VISIBLE : View.GONE);
        if (aviso != null) v.setTextViewText(R.id.vacio, aviso);
        botones(c, v);
        m.updateAppWidget(ids, v);
    }
}
