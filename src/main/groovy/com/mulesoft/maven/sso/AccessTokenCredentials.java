package com.mulesoft.maven.sso;

import org.apache.http.auth.UsernamePasswordCredentials;

public class AccessTokenCredentials extends UsernamePasswordCredentials {
    // https://docs.mulesoft.com/anypoint-exchange/to-publish-assets-maven#to-publish-federated-assets
    private final static String ANYPOINT_TOKEN_VIA_BASIC = "~~~Token~~~";
    private final long timeInMillis;

    public AccessTokenCredentials(String accessToken,
                                  long timeInMillis) {
        super(ANYPOINT_TOKEN_VIA_BASIC, accessToken);
        this.timeInMillis = timeInMillis;
    }

    public boolean isExpired(long maxAgeInMs) {
        long difference = System.currentTimeMillis() - this.timeInMillis;
        return difference > maxAgeInMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        AccessTokenCredentials that = (AccessTokenCredentials) o;

        return timeInMillis == that.timeInMillis;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (int) (timeInMillis ^ (timeInMillis >>> 32));
        return result;
    }
}
