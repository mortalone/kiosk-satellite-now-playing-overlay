// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.partymode;

import java.time.LocalTime;

final class PartyVisibility {
    private PartyVisibility() {}
    static boolean allowed(String condition, String entity, String state, String value, LocalTime now) {
        if (condition == null || condition.isEmpty() || "Always".equals(condition)) return true;
        if ("Time between".equals(condition)) {
            try {
                String[] pair = value.split("\\s*-\\s*");
                if (pair.length != 2) return false;
                LocalTime start = LocalTime.parse(pair[0].trim()), end = LocalTime.parse(pair[1].trim());
                return start.equals(end) || (start.isBefore(end) ? !now.isBefore(start) && now.isBefore(end) : !now.isBefore(start) || now.isBefore(end));
            } catch (Exception ignored) { return false; }
        }
        if (entity == null || entity.isEmpty() || state == null || state.isEmpty() || "unknown".equalsIgnoreCase(state) || "unavailable".equalsIgnoreCase(state)) return false;
        if ("Active".equals(condition) || "Inactive".equals(condition)) {
            boolean active = false;
            for (String s : new String[] {"on", "true", "home", "playing", "open", "detected", "occupied", "present"}) active |= s.equalsIgnoreCase(state.trim());
            return "Active".equals(condition) == active;
        }
        if ("State equals".equals(condition)) return state.trim().equalsIgnoreCase(value.trim());
        if ("State not equals".equals(condition)) return !state.trim().equalsIgnoreCase(value.trim());
        try {
            double actual = number(state);
            if ("Numeric above".equals(condition)) return actual > number(value);
            if ("Numeric below".equals(condition)) return actual < number(value);
            if ("Numeric between".equals(condition)) {
                String[] pair = value.split("\\.\\.");
                if (pair.length != 2) return false;
                double a = number(pair[0]), b = number(pair[1]);
                return actual >= Math.min(a, b) && actual <= Math.max(a, b);
            }
        } catch (Exception ignored) {}
        return false;
    }
    private static double number(String value) {
        double n = Double.parseDouble(value.trim().replace(',', '.'));
        if (Double.isNaN(n) || Double.isInfinite(n)) throw new IllegalArgumentException();
        return n;
    }
}
