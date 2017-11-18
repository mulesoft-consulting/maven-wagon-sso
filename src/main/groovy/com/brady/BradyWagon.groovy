package com.brady

import org.apache.maven.wagon.providers.http.HttpWagon
import org.apache.maven.wagon.providers.http.httpclient.HttpException
import org.apache.maven.wagon.providers.http.httpclient.client.methods.CloseableHttpResponse
import org.apache.maven.wagon.providers.http.httpclient.client.methods.HttpUriRequest

class BradyWagon extends HttpWagon {
    @Override
    protected CloseableHttpResponse execute(HttpUriRequest httpMethod) throws HttpException, IOException {
        throw new Exception('brady Groovy')
        return super.execute(httpMethod)
    }
}
