package com.gratiavalencia.queritany;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/**
 * Piloto 126.25 · APK 1.3 · 📋 La lista grande de Querit any (4 × 4): como QueritWidget, pero con hasta 18 planes
 * del próximo mes. Comparte los datos y el ↻ con los demás widgets.
 */
public class QueritWidgetGrande extends AppWidgetProvider {
    static final int MAX_FILAS = 18;

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        QueritWidget.pintar(c, m, ids, MAX_FILAS);
        QueritWidget.refrescar(this, c);
    }
}
