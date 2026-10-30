package com.gratiavalencia.queritany;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Piloto 126.25 · 📱 Los datos del widget de Querit any.
 * Guarda en el móvil el acceso y la llave que le pasa Querit any (💾 guardar → 📱 conectar el widget).
 * Le pide a Gratia los bloques CIFRADOS (POST /api/querit-widget { acceso }) y los abre AQUÍ con la llave,
 * igual que hace la web (AES-GCM: 12 bytes de iv + lo cifrado). La llave nunca sale del móvil.
 * Lo que se enseña (los planes de los próximos días) se guarda en el móvil para verlo sin internet.
 */
final class WidgetDatos {
    static final String PREFS = "querit_widget";
    static final String[] SEMANA = {"domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"};
    static final String[] MESES = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};

    private WidgetDatos() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Guarda «acceso-llave» (lo que viene en queritany://widget?c=…). */
    static boolean guardarClave(Context c, String clave) {
        if (clave == null || !clave.matches("[0-9a-f]{64}-[A-Za-z0-9_.]{43}")) return false;
        prefs(c).edit()
                .putString("acceso", clave.substring(0, 64))
                .putString("llave", clave.substring(65))
                .putString("estado", "conectando")
                .remove("dias")
                .apply();
        return true;
    }

    // ── El cifrado (como sincronizar.ts: base64 con «_» y «.» en vez de «+» y «/») ──
    static byte[] llaveBytes(String l) {
        StringBuilder s = new StringBuilder(l.replace('_', '+').replace('.', '/'));
        while (s.length() % 4 != 0) s.append('=');
        return Base64.decode(s.toString(), Base64.DEFAULT);
    }

    static String descifrar(byte[] llave, byte[] b) throws GeneralSecurityException, IOException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(llave, "AES"), new GCMParameterSpec(128, b, 0, 12));
        return new String(c.doFinal(b, 12, b.length - 12), "UTF-8");
    }

    // ── Fechas (sin java.time, para móviles antiguos) ──
    /** Días desde el 1/1/1970 de «aaaa-mm-dd». */
    static long diaN(String k) {
        long y = Long.parseLong(k.substring(0, 4));
        long m = Long.parseLong(k.substring(5, 7));
        long d = Long.parseLong(k.substring(8, 10));
        y -= m <= 2 ? 1 : 0;
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    static String deDia(long z) {
        z += 719468;
        long era = (z >= 0 ? z : z - 146096) / 146097;
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long y = yoe + era * 400;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        long d = doy - (153 * mp + 2) / 5 + 1;
        long m = mp < 10 ? mp + 3 : mp - 9;
        if (m <= 2) y += 1;
        return String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", y, m, d);
    }

    static int diaSemana(String k) {
        return (int) (((diaN(k) + 4) % 7 + 7) % 7); // 0 = domingo
    }

    static String hoyAqui() {
        Calendar h = Calendar.getInstance();
        return String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", h.get(Calendar.YEAR), h.get(Calendar.MONTH) + 1, h.get(Calendar.DAY_OF_MONTH));
    }

    static String fechaLarga(String k) {
        return SEMANA[diaSemana(k)] + " " + Integer.parseInt(k.substring(8)) + " de " + MESES[Integer.parseInt(k.substring(5, 7)) - 1];
    }

    /** ¿Cae este plan en el día k? (lo mismo que tocaEl de calendario.tsx) */
    static boolean tocaEl(JSONObject e, String k) {
        String f = e.optString("fecha", "");
        if (f.length() != 10) return false;
        int cmp = k.compareTo(f);
        if (cmp < 0) return false;
        if (cmp == 0) return true;
        String rep = e.optString("repite", "no");
        String hasta = e.optString("hasta", "");
        if (rep.equals("no") || (hasta.length() == 10 && k.compareTo(hasta) > 0)) return false;
        int n = Math.max(1, e.optInt("cada", 1));
        long dias = diaN(k) - diaN(f);
        int meses = (Integer.parseInt(k.substring(0, 4)) - Integer.parseInt(f.substring(0, 4))) * 12
                + Integer.parseInt(k.substring(5, 7)) - Integer.parseInt(f.substring(5, 7));
        switch (rep) {
            case "dia": return dias % n == 0;
            case "semana": return dias % (7L * n) == 0;
            case "mes": return k.substring(8).equals(f.substring(8)) && meses % n == 0;
            default: return k.substring(5).equals(f.substring(5)) && (meses / 12) % n == 0;
        }
    }

    static String colorDe(String id) {
        switch (id) {
            case "lavanda": return "#8f7ad6";
            case "salvia": return "#6f9a5b";
            case "cielo": return "#4f93c9";
            case "rosa": return "#e07aa8";
            case "miel": return "#d9a425";
            default: return "#e2622c";
        }
    }

    private static byte[] b64(String s) {
        return Base64.decode(s, Base64.DEFAULT);
    }

    private static String leer(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        in.close();
        return o.toString("UTF-8");
    }

    private static void sumar(List<JSONObject> lista, JSONArray eventos, String emoji) throws Exception {
        if (eventos == null) return;
        for (int i = 0; i < eventos.length(); i++) {
            JSONObject e = eventos.getJSONObject(i);
            e.put("_emoji", emoji);
            lista.add(e);
        }
    }

    /** Pide los bloques a Gratia, los abre y guarda los planes de los próximos 14 días. (En segundo plano.) */
    static void actualizar(Context c) {
        SharedPreferences p = prefs(c);
        String acceso = p.getString("acceso", null);
        String llave = p.getString("llave", null);
        if (acceso == null || llave == null) return;
        HttpURLConnection h = null;
        try {
            h = (HttpURLConnection) new URL("https://gratiavalencia.com/api/querit-widget").openConnection();
            h.setConnectTimeout(15000);
            h.setReadTimeout(20000);
            h.setRequestMethod("POST");
            h.setDoOutput(true);
            h.setRequestProperty("Content-Type", "application/json");
            OutputStream o = h.getOutputStream();
            o.write(new JSONObject().put("acceso", acceso).toString().getBytes("UTF-8"));
            o.close();
            int st = h.getResponseCode();
            if (st == 404) { p.edit().putString("estado", "desconectado").apply(); return; }
            if (st != 200) throw new IOException("HTTP " + st);
            JSONObject r = new JSONObject(leer(h.getInputStream()));
            byte[] k = llaveBytes(llave);

            List<JSONObject> eventos = new ArrayList<>();
            if (!r.isNull("datos")) {
                JSONObject d = new JSONObject(descifrar(k, b64(r.getString("datos"))));
                sumar(eventos, d.optJSONArray("eventos"), "");
            }
            if (!r.isNull("llavero")) {
                JSONObject l = new JSONObject(descifrar(k, b64(r.getString("llavero"))));
                JSONObject llaves = l.optJSONObject("cals");
                JSONArray cals = r.optJSONArray("cals");
                for (int i = 0; llaves != null && cals != null && i < cals.length(); i++) {
                    JSONObject cal = cals.getJSONObject(i);
                    String kc = llaves.optString(cal.optString("id"), "");
                    if (kc.length() != 43) continue;
                    try {
                        JSONObject cd = new JSONObject(descifrar(llaveBytes(kc), b64(cal.getString("contenido"))));
                        JSONObject meta = cd.optJSONObject("meta");
                        sumar(eventos, cd.optJSONArray("eventos"), meta != null ? meta.optString("emoji", "👯") : "👯");
                    } catch (Exception ignorar) { /* ese calendario no se puede abrir: se salta */ }
                }
            }

            String hoy = r.optString("hoy", "");
            if (hoy.length() != 10) hoy = hoyAqui();
            JSONArray dias = new JSONArray();
            int total = 0;
            for (int i = 0; i < 14 && total < 8; i++) {
                final String dia = deDia(diaN(hoy) + i);
                List<JSONObject> deHoy = new ArrayList<>();
                for (JSONObject e : eventos) if (tocaEl(e, dia)) deHoy.add(e);
                if (deHoy.isEmpty()) continue;
                Collections.sort(deHoy, new Comparator<JSONObject>() {
                    @Override public int compare(JSONObject a, JSONObject b) { return a.optString("hora", "").compareTo(b.optString("hora", "")); }
                });
                JSONArray planes = new JSONArray();
                for (JSONObject e : deHoy) {
                    String hora = e.optString("hora", "");
                    String emoji = e.optString("_emoji", "");
                    planes.put(new JSONObject()
                            .put("h", hora.isEmpty() ? "todo el día" : hora)
                            .put("t", (emoji.isEmpty() ? "" : emoji + " ") + e.optString("titulo", "plan"))
                            .put("c", colorDe(e.optString("color", "tomate"))));
                    total++;
                }
                dias.put(new JSONObject().put("k", dia).put("i", i).put("planes", planes));
            }
            p.edit().putString("dias", dias.toString()).putString("hoy", hoy).putLong("cuando", System.currentTimeMillis()).putString("estado", "ok").apply();
        } catch (GeneralSecurityException e) {
            p.edit().putString("estado", "llave").apply();
        } catch (Exception e) {
            p.edit().putString("estado", p.getString("dias", null) != null ? "ok" : "sinred").apply();
        } finally {
            if (h != null) h.disconnect();
        }
    }
}
