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

import java.io.IOError;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.config.CFMetaData;
import org.apache.cassandra.config.Schema;
import org.apache.cassandra.cql3.*;
import org.apache.cassandra.transport.messages.ResultMessage;
import org.apache.cassandra.db.*;
import org.apache.cassandra.db.filter.QueryPath;
import org.apache.cassandra.db.marshal.CompositeType;
import org.apache.cassandra.exceptions.*;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.service.StorageProxy;

/**
 * Abstract class for statements that apply on a given column family.
 */
public abstract class ModificationStatement extends CFStatement implements CQLStatement
{
    public static final ConsistencyLevel defaultConsistency = ConsistencyLevel.ONE;

    public static enum Type
    {
        LOGGED, UNLOGGED, COUNTER
    }

    protected Type type;

    private final ConsistencyLevel cLevel;
    private Long timestamp;
    private final int timeToLive;

    public ModificationStatement(CFName name, Attributes attrs)
    {
        this(name, attrs.cLevel, attrs.timestamp, attrs.timeToLive);
    }

    public ModificationStatement(CFName name, ConsistencyLevel cLevel, Long timestamp, int timeToLive)
    {
        super(name);
        this.cLevel = cLevel;
        this.timestamp = timestamp;
        this.timeToLive = timeToLive;
    }

    public void checkAccess(ClientState state) throws InvalidRequestException, UnauthorizedException
    {
        state.hasColumnFamilyAccess(keyspace(), columnFamily(), Permission.UPDATE);
    }

    public void validate(ClientState state) throws InvalidRequestException
    {
        if (timeToLive < 0)
            throw new InvalidRequestException("A TTL must be greater or equal to 0");

        getConsistencyLevel().validateForWrite(keyspace());
    }

    public ResultMessage execute(ClientState state, List<ByteBuffer> variables) throws RequestExecutionException, RequestValidationException
    {
        Collection<? extends IMutation> mutations = getMutations(state, variables, false);
        ConsistencyLevel cl = getConsistencyLevel();

        // The type should have been set by now or we have a bug
        assert type != null;

        switch (type)
        {
            case LOGGED:
                if (mutations.size() > 1)
                    StorageProxy.mutateAtomically((Collection<RowMutation>) mutations, cl);
                else
                    StorageProxy.mutate(mutations, cl);
                break;
            case UNLOGGED:
            case COUNTER:
                StorageProxy.mutate(mutations, cl);
                break;
            default:
                throw new AssertionError();
        }

        return null;
    }


    public ResultMessage executeInternal(ClientState state) throws RequestValidationException, RequestExecutionException
    {
        for (IMutation mutation : getMutations(state, Collections.<ByteBuffer>emptyList(), true))
            mutation.apply();
        return null;
    }

    public ConsistencyLevel getConsistencyLevel()
    {
        if (cLevel != null)
            return cLevel;

        CFMetaData cfm = Schema.instance.getCFMetaData(keyspace(), columnFamily());
        return cfm == null ? ConsistencyLevel.ONE : cfm.getWriteConsistencyLevel();
    }

    /**
     * True if an explicit consistency level was parsed from the statement.
     *
     * @return true if a consistency was parsed, false otherwise.
     */
    public boolean isSetConsistencyLevel()
    {
        return cLevel != null;
    }

    public long getTimestamp(ClientState clientState)
    {
        return timestamp == null ? clientState.getTimestamp() : timestamp;
    }

    public void setTimestamp(long timestamp)
    {
        this.timestamp = timestamp;
    }

    public boolean isSetTimestamp()
    {
        return timestamp != null;
    }

    public int getTimeToLive()
    {
        return timeToLive;
    }

    protected Map<ByteBuffer, ColumnGroupMap> readRows(List<ByteBuffer> keys, ColumnNameBuilder builder, CompositeType composite, boolean local)
    throws RequestExecutionException, RequestValidationException
    {
        List<ReadCommand> commands = new ArrayList<ReadCommand>(keys.size());
        for (ByteBuffer key : keys)
        {
            commands.add(new SliceFromReadCommand(keyspace(),
                                                  key,
                                                  new QueryPath(columnFamily()),
                                                  builder.copy().build(),
                                                  builder.copy().buildAsEndOfRange(),
                                                  false,
                                                  Integer.MAX_VALUE));
        }

        try
        {
            List<Row> rows = local
                           ? SelectStatement.readLocally(keyspace(), commands)
                           : StorageProxy.read(commands, getConsistencyLevel());

            Map<ByteBuffer, ColumnGroupMap> map = new HashMap<ByteBuffer, ColumnGroupMap>();
            for (Row row : rows)
            {
                if (row.cf == null || row.cf.isEmpty())
                    continue;

                ColumnGroupMap.Builder groupBuilder = new ColumnGroupMap.Builder(composite, true);
                for (IColumn column : row.cf)
                    groupBuilder.add(column);

                List<ColumnGroupMap> groups = groupBuilder.groups();
                assert groups.isEmpty() || groups.size() == 1;
                if (!groups.isEmpty())
                    map.put(row.key.key, groups.get(0));
            }
            return map;
        }
        catch (IOException e)
        {
            throw new IOError(e);
        }
    }

    /**
     * Convert statement into a list of mutations to apply on the server
     *
     * @param clientState current client status
     * @param variables value for prepared statement markers
     * @param local if true, any requests (for collections) performed by getMutation should be done locally only.
     *
     * @return list of the mutations
     * @throws InvalidRequestException on invalid requests
     */
    protected abstract Collection<? extends IMutation> getMutations(ClientState clientState, List<ByteBuffer> variables, boolean local)
    throws RequestExecutionException, RequestValidationException;

    public abstract ParsedStatement.Prepared prepare(CFDefinition.Name[] boundNames) throws InvalidRequestException;
}
