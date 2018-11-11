package org.apache.cassandra.stress.operations;
/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */


import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.cassandra.utils.ByteBufferUtil;

public class CqlCounterAdder extends CqlOperation<Integer>
{
    public CqlCounterAdder(State state, long idx)
    {
        super(state, idx);
    }

    @Override
    protected String buildQuery()
    {
        String counterCF = state.isCql2() ? state.type.table : "Counter3";

        StringBuilder query = new StringBuilder("UPDATE ").append(wrapInQuotesIfRequired(counterCF));

        if (state.isCql2())
            query.append(" USING CONSISTENCY ").append(state.settings.command.consistencyLevel);

        query.append(" SET ");

        // TODO : increment distribution subset of columns
        for (int i = 0; i < state.settings.columns.maxColumnsPerKey; i++)
        {
            if (i > 0)
                query.append(",");

            query.append('C').append(i).append("=C").append(i).append("+?");
        }
        query.append(" WHERE KEY=?");
        return query.toString();
    }

    @Override
    protected List<Object> getQueryParameters(byte[] key)
    {
        final List<Object> list = new ArrayList<>();
        for (int i = 0; i < state.settings.columns.maxColumnsPerKey; i++)
            list.add(state.counteradd.next());
        list.add(ByteBuffer.wrap(key));
        return list;
    }

    @Override
    protected CqlRunOp<Integer> buildRunOp(ClientWrapper client, String query, Object queryId, List<Object> params, String keyid, ByteBuffer key)
    {
        return new CqlRunOpAlwaysSucceed(client, query, queryId, params, keyid, key, 1);
    }
}