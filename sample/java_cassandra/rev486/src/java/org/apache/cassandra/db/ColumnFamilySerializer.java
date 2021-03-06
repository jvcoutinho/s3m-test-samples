package org.apache.cassandra.db;
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


import java.io.*;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.config.CFMetaData;
import org.apache.cassandra.io.ICompactSerializer2;
import org.apache.cassandra.io.util.PageCacheInformer;
import org.apache.cassandra.utils.PageCacheMetrics;

public class ColumnFamilySerializer implements ICompactSerializer2<ColumnFamily>
{
    private static final Logger logger = LoggerFactory.getLogger(ColumnFamilySerializer.class);

    /*
     * Serialized ColumnFamily format:
     *
     * [serialized for intra-node writes only, e.g. returning a query result]
     * <cf nullability boolean: false if the cf is null>
     * <cf id>
     *
     * [in sstable only]
     * <column bloom filter>
     * <sparse column index, start/finish columns every ColumnIndexSizeInKB of data>
     *
     * [always present]
     * <local deletion time>
     * <client-provided deletion time>
     * <column count>
     * <columns, serialized individually>
    */
    public void serialize(ColumnFamily columnFamily, DataOutput dos)
    {
        try
        {
            if (columnFamily == null)
            {
                dos.writeBoolean(false);
                return;
            }

            dos.writeBoolean(true);
            dos.writeInt(columnFamily.id());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        serializeForSSTable(columnFamily, dos);
    }

    public int serializeForSSTable(ColumnFamily columnFamily, DataOutput dos)
    {
        PageCacheInformer pci = dos instanceof PageCacheInformer
                                ? (PageCacheInformer) dos
                                : null;

        try
        {
            serializeCFInfo(columnFamily, dos);

            Collection<IColumn> columns = columnFamily.getSortedColumns();
            int count = columns.size();
            dos.writeInt(count);
            for (IColumn column : columns)
            {
                long startAt = pci != null ? pci.getCurrentPosition() : -1;

                columnFamily.getColumnSerializer().serialize(column, dos);

                //Track the section of serialized data that should
                //be included in the page cache (compaction)
                if (column.isInPageCache() && pci != null)
                    pci.keepCacheWindow(startAt);
            }

            return count;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void serializeCFInfo(ColumnFamily columnFamily, DataOutput dos) throws IOException
    {
        dos.writeInt(columnFamily.localDeletionTime.get());
        dos.writeLong(columnFamily.markedForDeleteAt.get());
    }

    public int serializeWithIndexes(ColumnFamily columnFamily, DataOutput dos)
    {
        ColumnIndexer.serialize(columnFamily, dos);
        return serializeForSSTable(columnFamily, dos);
    }

    public ColumnFamily deserialize(DataInput dis) throws IOException
    {
        if (!dis.readBoolean())
            return null;

        // create a ColumnFamily based on the cf id
        int cfId = dis.readInt();
        if (CFMetaData.getCF(cfId) == null)
            throw new UnserializableColumnFamilyException("Couldn't find cfId=" + cfId, cfId);
        ColumnFamily cf = ColumnFamily.create(cfId);
        deserializeFromSSTableNoColumns(cf, dis);
        deserializeColumns(dis, cf, null);
        return cf;
    }

    public boolean deserializeColumns(DataInput dis, ColumnFamily cf, PageCacheMetrics pageCacheMetrics) throws IOException
    {

        int size = dis.readInt();

        boolean hasColumnsInPageCache = false;


        if (pageCacheMetrics != null && dis instanceof RandomAccessFile)
        {
            RandomAccessFile raf = (RandomAccessFile) dis;

            for (int i = 0; i < size; ++i)
            {
                long startAt = raf.getFilePointer();

                IColumn column = cf.getColumnSerializer().deserialize(dis);

                long endAt = raf.getFilePointer();

                column.setIsInPageCache(pageCacheMetrics.isRangeInCache(startAt, endAt));

                if(!hasColumnsInPageCache)
                    hasColumnsInPageCache = column.isInPageCache();

                cf.addColumn(column);
            }
        }
        else
        {
            for (int i = 0; i < size; ++i)
            {
                IColumn column = cf.getColumnSerializer().deserialize(dis);

                cf.addColumn(column);
            }
        }

        return hasColumnsInPageCache;
    }

    public ColumnFamily deserializeFromSSTableNoColumns(ColumnFamily cf, DataInput input) throws IOException
    {        
        cf.delete(input.readInt(), input.readLong());
        return cf;
    }
}
