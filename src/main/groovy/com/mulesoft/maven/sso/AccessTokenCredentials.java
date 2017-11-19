package com.mulesoft.maven.sso;

import org.apache.http.auth.UsernamePasswordCredentials;

import java.util.Date;

public class AccessTokenCredentials extends UsernamePasswordCredentials {
    // https://docs.mulesoft.com/anypoint-exchange/to-publish-assets-maven#to-publish-federated-assets
    private final static String ANYPOINT_TOKEN_VIA_BASIC = "~~~Token~~~";
    private final Date tokenTime;

    public AccessTokenCredentials(String accessToken,
                                  Date tokenTime) {
        super(ANYPOINT_TOKEN_VIA_BASIC, accessToken);
        this.tokenTime = tokenTime;
    }

    public Date getTokenTime() {
        return tokenTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        AccessTokenCredentials that = (AccessTokenCredentials) o;

        return tokenTime != null ? tokenTime.equals(that.tokenTime) : that.tokenTime == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (tokenTime != null ? tokenTime.hashCode() : 0);
        return result;
    }
}
