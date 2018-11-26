/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cassandra.cql3.statements;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import org.apache.cassandra.cql3.CQLStatement;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.transport.messages.ResultMessage;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.config.Schema;
import org.apache.cassandra.cql3.CFName;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.exceptions.RequestValidationException;
import org.apache.cassandra.exceptions.UnauthorizedException;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.service.StorageProxy;

import com.google.common.base.Predicates;
import com.google.common.collect.Maps;

/**
 * Abstract class for statements that alter the schema.
 */
public abstract class SchemaAlteringStatement extends CFStatement implements CQLStatement
{
    private static final long timeLimitForSchemaAgreement = 10 * 1000;

    private final boolean isColumnFamilyLevel;

    protected SchemaAlteringStatement()
    {
        super(null);
        this.isColumnFamilyLevel = false;
    }

    protected SchemaAlteringStatement(CFName name)
    {
        super(name);
        this.isColumnFamilyLevel = true;
    }

    @Override
    public void prepareKeyspace(ClientState state) throws InvalidRequestException
    {
        if (isColumnFamilyLevel)
            super.prepareKeyspace(state);
    }

    public Prepared prepare() throws InvalidRequestException
    {
        return new Prepared(this);
    }

    public abstract void announceMigration() throws RequestValidationException;

    @Override
    public void validate(ClientState state) throws RequestValidationException
    {}

    public ResultMessage execute(ClientState state, List<ByteBuffer> variables) throws RequestValidationException
    {
        try
        {
            announceMigration();
        }
        catch (ConfigurationException e)
        {
            InvalidRequestException ex = new InvalidRequestException(e.toString());
            ex.initCause(e);
            throw ex;
        }
        return null;
    }
}
