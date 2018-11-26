/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.externalresource.transport.sftp;

import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.internal.externalresource.AbstractExternalResource;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class SftpResource extends AbstractExternalResource {

    private final SftpClientFactory clientFactory;
    private final ExternalResourceMetaData metaData;
    private final URI uri;
    private final PasswordCredentials credentials;

    private LockableSftpClient client;

    public SftpResource(SftpClientFactory clientFactory, ExternalResourceMetaData metaData, URI uri, PasswordCredentials credentials) {
        this.clientFactory = clientFactory;
        this.metaData = metaData;
        this.uri = uri;
        this.credentials = credentials;
    }

    @Override
    protected InputStream openStream() throws IOException {
        client = clientFactory.createSftpClient(uri, credentials);
        try {
            return client.getSftpClient().get(uri.getPath());
        } catch (com.jcraft.jsch.SftpException e) {
            throw new SftpException(String.format("Could not get resource at '%s'.", uri), e);
        }
    }

    public String getName() {
        return uri.toString();
    }

    public long getLastModified() {
        return metaData.getLastModified().getTime();
    }

    public long getContentLength() {
        return metaData.getContentLength();
    }

    public boolean exists() {
        return true;
    }

    public boolean isLocal() {
        return false;
    }

    public ExternalResourceMetaData getMetaData() {
        return metaData;
    }

    public void close() throws IOException {
        clientFactory.releaseSftpClient(client);
    }
}
