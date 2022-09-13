/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.cloud.bigquery.connector.common;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.api.core.ApiFunction;
import com.google.auth.http.HttpTransportFactory;
import io.grpc.HttpConnectProxiedSocketAddress;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ProxiedSocketAddress;
import io.grpc.ProxyDetector;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Optional;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;

public class BigQueryProxyTransporterBuilder {

  public static ApiFunction<ManagedChannelBuilder, ManagedChannelBuilder>
      createGrpcChannelConfigurator(
          Optional<URI> proxyAddress,
          Optional<String> proxyUsername,
          Optional<String> proxyPassword) {
    if (!proxyAddress.isPresent()) {
      return null;
    }
    checkProxyParamsValidity(proxyUsername, proxyPassword);

    URI proxyUri = proxyAddress.get();
    String httpProxyHost = proxyUri.getHost();
    int httpProxyPort = proxyUri.getPort();
    final InetSocketAddress proxySocketAddress =
        new InetSocketAddress(httpProxyHost, httpProxyPort);

    return new ApiFunction<ManagedChannelBuilder, ManagedChannelBuilder>() {
      @Override
      public ManagedChannelBuilder apply(ManagedChannelBuilder input) {
        return input.proxyDetector(
            new ProxyDetector() {
              @Override
              public ProxiedSocketAddress proxyFor(SocketAddress targetServerAddress) {
                HttpConnectProxiedSocketAddress.Builder proxySocketBuilder =
                    HttpConnectProxiedSocketAddress.newBuilder()
                        .setTargetAddress((InetSocketAddress) targetServerAddress)
                        .setProxyAddress(proxySocketAddress);

                if (proxyUsername.isPresent() && proxyPassword.isPresent()) {
                  proxySocketBuilder.setUsername(proxyUsername.get());
                  proxySocketBuilder.setPassword(proxyPassword.get());
                }

                return proxySocketBuilder.build();
              }
            });
      }
    };
  }

  public static HttpTransportFactory createHttpTransportFactory(
      Optional<URI> proxyAddress, Optional<String> proxyUsername, Optional<String> proxyPassword) {
    if (!proxyAddress.isPresent()) {
      return null;
    }
    checkProxyParamsValidity(proxyUsername, proxyPassword);

    URI proxyUri = proxyAddress.get();
    String httpProxyHost = proxyUri.getHost();
    int httpProxyPort = proxyUri.getPort();
    final HttpHost proxyHost = new HttpHost(httpProxyHost, httpProxyPort);
    HttpClientBuilder httpClientBuilder = HttpClientBuilder.create().setProxy(proxyHost);

    if (proxyUsername.isPresent() && proxyPassword.isPresent()) {
      Credentials proxyCredentials =
          new UsernamePasswordCredentials(proxyUsername.get(), proxyPassword.get());
      CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(
          new AuthScope(proxyUri.getHost(), proxyUri.getPort()), proxyCredentials);
      httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
    }

    return new BigQueryHttpTransportFactory(httpClientBuilder);
  }

  public static class BigQueryHttpTransportFactory implements HttpTransportFactory {
    private HttpClientBuilder httpClientBuilder;

    public BigQueryHttpTransportFactory() {
      // empty constructor:
      // to solve the InstantiationException in ServiceAccountCredentials at readObject
    }

    public BigQueryHttpTransportFactory(HttpClientBuilder httpClientBuilder) {
      this.httpClientBuilder = httpClientBuilder;
    }

    @Override
    public HttpTransport create() {
      return new ApacheHttpTransport(httpClientBuilder.build());
    }
  }

  public static void checkProxyParamsValidity(
      Optional<String> proxyUsername, Optional<String> proxyPassword)
      throws IllegalArgumentException {
    if (proxyUsername.isPresent() != proxyPassword.isPresent()) {
      throw new IllegalArgumentException(
          "Both proxyUsername and proxyPassword should be defined or not defined together");
    }
  }
}
