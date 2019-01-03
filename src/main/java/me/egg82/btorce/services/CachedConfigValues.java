package me.egg82.btorce.services;

public class CachedConfigValues {
    private CachedConfigValues() {}

    private boolean debug = false;
    public boolean getDebug() { return debug; }

    private int timeout = 20;
    public int getTimeout() { return timeout; }

    public static CachedConfigValues.Builder builder() { return new CachedConfigValues.Builder(); }

    public static class Builder {
        private final CachedConfigValues values = new CachedConfigValues();

        private Builder() {}

        public CachedConfigValues.Builder debug(boolean value) {
            values.debug = value;
            return this;
        }

        public CachedConfigValues.Builder timeout(int value) {
            if (value < 0) {
                throw new IllegalArgumentException("value cannot be < 0");
            }

            values.timeout = value;
            return this;
        }

        public CachedConfigValues build() {
            return values;
        }
    }
}
