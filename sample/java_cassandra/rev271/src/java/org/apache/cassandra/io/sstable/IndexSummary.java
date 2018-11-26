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
package org.apache.cassandra.io.sstable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.dht.IPartitioner;
import org.apache.cassandra.utils.ByteBufferUtil;

/**
 * Two approaches to building an IndexSummary:
 * 1. Call maybeAddEntry with every potential index entry
 * 2. Call shouldAddEntry, [addEntry,] incrementRowid
 */
public class IndexSummary
{
    public static final IndexSummarySerializer serializer = new IndexSummarySerializer();
    private final ArrayList<Long> positions;
    private final ArrayList<DecoratedKey> keys;
    private long keysWritten = 0;
    private int indexInterval;

    private IndexSummary()
    {
        positions = new ArrayList<Long>();
        keys = new ArrayList<DecoratedKey>();
    }

    public IndexSummary(long expectedKeys, int indexInterval)
    {
        long expectedEntries = expectedKeys / indexInterval;
        if (expectedEntries > Integer.MAX_VALUE)
            // TODO: that's a _lot_ of keys, or a very low interval
            throw new RuntimeException("Cannot use index_interval of " + indexInterval + " with " + expectedKeys + " (expected) keys.");
        positions = new ArrayList<Long>((int)expectedEntries);
        keys = new ArrayList<DecoratedKey>((int)expectedEntries);
        this.indexInterval = indexInterval;
    }

    public void incrementRowid()
    {
        keysWritten++;
    }

    public boolean shouldAddEntry()
    {
        return keysWritten % indexInterval == 0;
    }

    public void addEntry(DecoratedKey key, long indexPosition)
    {
        keys.add(SSTable.getMinimalKey(key));
        positions.add(indexPosition);
    }

    public void maybeAddEntry(DecoratedKey decoratedKey, long indexPosition)
    {
        if (shouldAddEntry())
            addEntry(decoratedKey, indexPosition);
        incrementRowid();
    }

    public List<DecoratedKey> getKeys()
    {
        return keys;
    }

    public long getPosition(int index)
    {
        return positions.get(index);
    }

    public int getIndexInterval() {
        return indexInterval;
    }

	public void complete()
    {
        keys.trimToSize();
        positions.trimToSize();
    }

    public static class IndexSummarySerializer
    {
        public void serialize(IndexSummary t, DataOutput out) throws IOException
        {
            assert t.keys.size() == t.positions.size() : "keysize and the position sizes are not same.";
            out.writeInt(t.indexInterval);
            out.writeInt(t.keys.size());
            for (int i = 0; i < t.keys.size(); i++)
            {
                out.writeLong(t.positions.get(i));
                ByteBufferUtil.writeWithLength(t.keys.get(i).key, out);
            }
        }

        public IndexSummary deserialize(DataInput in, IPartitioner partitioner) throws IOException
        {
            IndexSummary summary = new IndexSummary();
            summary.indexInterval = in.readInt();

            int size = in.readInt();
            for (int i = 0; i < size; i++)
            {
                long location = in.readLong();
                ByteBuffer key = ByteBufferUtil.readWithLength(in);
                summary.addEntry(partitioner.decorateKey(key), location);
            }
            return summary;
        }
    }
}
