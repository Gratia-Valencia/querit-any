package com.gratiavalencia.queritany;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Piloto 126.25 · APK 1.3 · 📅 El calendario del mes de Querit any (4 × 3): los días con planes llevan
 * puntitos de sus colores (hasta 3) y hoy va marcado. ◀ ▶ cambian de mes y tocar el nombre del mes vuelve al de hoy.
 * Los colores vienen ya calculados en WidgetDatos («meses», del mes pasado a 11 meses después), así que cambiar
 * de mes no necesita internet.
 */
public class QueritWidgetMes extends AppWidgetProvider {
    static final String MES_ANTES = "com.gratiavalencia.queritany.WIDGET_MES_ANTES";
    static final String MES_DESPUES = "com.gratiavalencia.queritany.WIDGET_MES_DESPUES";
    static final String MES_HOY = "com.gratiavalencia.queritany.WIDGET_MES_HOY";
    static final String MES_MODO = "com.gratiavalencia.queritany.WIDGET_MES_MODO";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        pintar(c, m, ids);
        QueritWidget.refrescar(this, c);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        String a = i.getAction();
        SharedPreferences p = WidgetDatos.prefs(c);
        if (MES_MODO.equals(a)) {
            p.edit().putString("mesModo", "nombres".equals(p.getString("mesModo", "puntos")) ? "puntos" : "nombres").apply();
        } else if (MES_ANTES.equals(a) || MES_DESPUES.equals(a) || MES_HOY.equals(a)) {
            int off = MES_HOY.equals(a) ? 0 : p.getInt("mesOff", 0) + (MES_ANTES.equals(a) ? -1 : 1);
            p.edit().putInt("mesOff", Math.max(-12, Math.min(24, off))).apply();
        } else {
            return;
        }
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        pintar(c, m, m.getAppWidgetIds(new ComponentName(c, QueritWidgetMes.class)));
    }

    private static int color(JSONObject pl) {
        try { return Color.parseColor(pl != null ? pl.optString("c", "#e2622c") : "#e2622c"); } catch (Exception e) { return Color.parseColor("#e2622c"); }
    }

    /** Un nombre de plan en su casilla, con su color (o «+N» si no caben todos). */
    private static void nombre(RemoteViews cel, int id, JSONObject pl, String mas) {
        if (pl == null) return;
        cel.setViewVisibility(id, View.VISIBLE);
        cel.setTextViewText(id, mas != null ? mas : pl.optString("t", "plan"));
        cel.setInt(id, "setBackgroundColor", mas != null ? Color.TRANSPARENT : (color(pl) & 0x00ffffff) | 0x33000000);
        cel.setTextColor(id, mas != null ? Color.parseColor("#8a6f5e") : Color.parseColor("#2b211b"));
    }

    private static PendingIntent accion(Context c, String a, int codigo) {
        return PendingIntent.getBroadcast(c, codigo, new Intent(c, QueritWidgetMes.class).setAction(a), QueritWidget.flags());
    }

    static void pintar(Context c, AppWidgetManager m, int[] ids) {
        if (ids == null || ids.length == 0) return;
        SharedPreferences p = WidgetDatos.prefs(c);
        String pkg = c.getPackageName();
        RemoteViews v = new RemoteViews(pkg, R.layout.widget_mes);

        String hoy = WidgetDatos.hoyAqui();
        int a0 = Integer.parseInt(hoy.substring(0, 4)), m0 = Integer.parseInt(hoy.substring(5, 7)) - 1;
        int t = a0 * 12 + m0 + p.getInt("mesOff", 0);
        int ya = t / 12, ym = t % 12;
        v.setTextViewText(R.id.mes_titulo, WidgetDatos.MESES[ym] + (ya != a0 ? " " + ya : ""));

        String km = WidgetDatos.clavesMes(ya, ym);
        JSONObject mes = null;
        try { mes = new JSONObject(p.getString("meses", "{}")).optJSONObject(km); } catch (Exception ignorar) { /* sin datos aún */ }

        // (1.3: «Aa» = con los nombres de hasta 2 planes por día; «●» = con puntitos)
        boolean nombres = "nombres".equals(p.getString("mesModo", "puntos"));
        v.setTextViewText(R.id.mes_modo, nombres ? "●" : "Aa");
        v.removeAllViews(R.id.rejilla);
        int hueco = (WidgetDatos.diaSemana(km + "-01") + 6) % 7; // (la semana empieza en lunes)
        int dias = WidgetDatos.diasDelMes(ya, ym);
        for (int fila = 0; fila * 7 < hueco + dias; fila++) {
            RemoteViews rf = new RemoteViews(pkg, R.layout.widget_mes_fila);
            for (int col = 0; col < 7; col++) {
                int d = fila * 7 + col - hueco + 1;
                RemoteViews cel = new RemoteViews(pkg, nombres ? R.layout.widget_mes_dia_nombres : R.layout.widget_mes_dia);
                if (d >= 1 && d <= dias) {
                    cel.setTextViewText(R.id.num, String.valueOf(d));
                    if ((km + String.format(java.util.Locale.ROOT, "-%02d", d)).equals(hoy)) {
                        cel.setInt(R.id.num, "setBackgroundResource", R.drawable.widget_hoy);
                        cel.setTextColor(R.id.num, Color.WHITE);
                    }
                    JSONObject delDia = mes != null ? mes.optJSONObject(String.valueOf(d)) : null;
                    JSONArray planes = delDia != null ? delDia.optJSONArray("p") : null;
                    if (planes != null && planes.length() > 0) {
                        if (nombres) {
                            // (hasta 2 nombres con su color; si hay más, el segundo dice cuántos faltan)
                            int total = delDia.optInt("n", planes.length());
                            nombre(cel, R.id.n1, planes.optJSONObject(0), null);
                            if (total > 2) nombre(cel, R.id.n2, planes.optJSONObject(1), "+" + (total - 1));
                            else if (planes.length() > 1) nombre(cel, R.id.n2, planes.optJSONObject(1), null);
                        } else {
                            SpannableStringBuilder sb = new SpannableStringBuilder();
                            for (int k = 0; k < planes.length(); k++) {
                                int ini = sb.length();
                                sb.append("●");
                                sb.setSpan(new ForegroundColorSpan(color(planes.optJSONObject(k))), ini, sb.length(), 0);
                            }
                            cel.setTextViewText(R.id.puntos, sb);
                        }
                    }
                } else {
                    cel.setTextViewText(R.id.num, "");
                }
                rf.addView(R.id.fila, cel);
            }
            v.addView(R.id.rejilla, rf);
        }

        String aviso = QueritWidget.aviso(p);
        v.setViewVisibility(R.id.vacio, aviso != null ? View.VISIBLE : View.GONE);
        if (aviso != null) v.setTextViewText(R.id.vacio, aviso);

        QueritWidget.botones(c, v);
        v.setOnClickPendingIntent(R.id.mes_antes, accion(c, MES_ANTES, 2));
        v.setOnClickPendingIntent(R.id.mes_despues, accion(c, MES_DESPUES, 3));
        v.setOnClickPendingIntent(R.id.mes_titulo, accion(c, MES_HOY, 4));
        v.setOnClickPendingIntent(R.id.mes_modo, accion(c, MES_MODO, 5));
        m.updateAppWidget(ids, v);
    }
}
