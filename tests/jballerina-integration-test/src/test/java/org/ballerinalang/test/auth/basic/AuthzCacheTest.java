/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.ballerinalang.test.auth.basic;

import io.netty.handler.codec.http.HttpHeaderNames;
import org.ballerinalang.test.auth.AuthBaseTest;
import org.ballerinalang.test.util.HttpResponse;
import org.ballerinalang.test.util.HttpsClientRequest;
import org.ballerinalang.test.util.TestConstant;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Test cases for authorization cache.
 */
@Test(groups = "auth-test")
public class AuthzCacheTest extends AuthBaseTest {

    private final int servicePort = 20011;

    @Test(description = "Authz cache test with success response")
    public void testAuthzCacheSuccess() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put(HttpHeaderNames.CONTENT_TYPE.toString(), TestConstant.CONTENT_TYPE_TEXT_PLAIN);
        headers.put("Authorization", "Basic YWxpY2U6MTIz");
        HttpResponse response = HttpsClientRequest.doGet(
                basicAuthServerInstance.getServiceURLHttps(servicePort, "echo/test"),
                headers, basicAuthServerInstance.getServerHome());
        assertOK(response);

        // The 2nd request is treated from the positive authz cache.
        headers = new HashMap<>();
        headers.put(HttpHeaderNames.CONTENT_TYPE.toString(), TestConstant.CONTENT_TYPE_TEXT_PLAIN);
        headers.put("Authorization", "Basic YWxpY2U6MTIz");
        response = HttpsClientRequest.doGet(
                basicAuthServerInstance.getServiceURLHttps(servicePort, "echo/test"),
                headers, basicAuthServerInstance.getServerHome());
        assertOK(response);
    }

    @Test(description = "Authz cache test with forbidden response")
    public void testAuthzCacheForbidden() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put(HttpHeaderNames.CONTENT_TYPE.toString(), TestConstant.CONTENT_TYPE_TEXT_PLAIN);
        headers.put("Authorization", "Basic Y2hhbmFrYToxMjM=");
        HttpResponse response = HttpsClientRequest.doGet(
                basicAuthServerInstance.getServiceURLHttps(servicePort, "echo/test"),
                headers, basicAuthServerInstance.getServerHome());
        assertForbidden(response);

        // The 2nd request is treated from the negative authz cache.
        headers = new HashMap<>();
        headers.put(HttpHeaderNames.CONTENT_TYPE.toString(), TestConstant.CONTENT_TYPE_TEXT_PLAIN);
        headers.put("Authorization", "Basic Y2hhbmFrYToxMjM=");
        response = HttpsClientRequest.doGet(
                basicAuthServerInstance.getServiceURLHttps(servicePort, "echo/test"),
                headers, basicAuthServerInstance.getServerHome());
        assertForbidden(response);
    }

    @Test(description = "Authz cache test with unauthorized response")
    public void testAuthzCacheUnauthorized() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put(HttpHeaderNames.CONTENT_TYPE.toString(), TestConstant.CONTENT_TYPE_TEXT_PLAIN);
        headers.put("Authorization", "Basic Ym9iOjEyMw==");
        HttpResponse response = HttpsClientRequest.doGet(
                basicAuthServerInstance.getServiceURLHttps(servicePort, "echo/test"),
                headers, basicAuthServerInstance.getServerHome());
        assertUnauthorized(response);

        // The 2nd request is treated from the negative authz cache.
        headers = new HashMap<>();
        headers.put(HttpHeaderNames.CONTENT_TYPE.toString(), TestConstant.CONTENT_TYPE_TEXT_PLAIN);
        headers.put("Authorization", "Basic Ym9iOjEyMw==");
        response = HttpsClientRequest.doGet(
                basicAuthServerInstance.getServiceURLHttps(servicePort, "echo/test"),
                headers, basicAuthServerInstance.getServerHome());
        assertUnauthorized(response);
    }
}
