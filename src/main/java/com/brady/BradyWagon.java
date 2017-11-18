package com.brady;

import org.apache.maven.wagon.providers.http.HttpWagon;
import org.apache.maven.wagon.providers.http.httpclient.HttpException;
import org.apache.maven.wagon.providers.http.httpclient.client.methods.CloseableHttpResponse;
import org.apache.maven.wagon.providers.http.httpclient.client.methods.HttpUriRequest;

import java.io.IOException;

public class BradyWagon extends HttpWagon {
    @Override
    protected CloseableHttpResponse execute(HttpUriRequest httpMethod) throws HttpException, IOException {
        throw new RuntimeException("Brady was here!");
        //return super.execute(httpMethod);
    }
}
