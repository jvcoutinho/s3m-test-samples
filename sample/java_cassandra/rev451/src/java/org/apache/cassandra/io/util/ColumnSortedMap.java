package org.apache.cassandra.io.util;
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


import java.io.DataInput;
import java.io.IOError;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.Map.Entry;

import org.apache.cassandra.db.ColumnSerializer;
import org.apache.cassandra.db.IColumn;

/**
 * Facade over a DataInput that contains IColumns in sorted order.
 * We use this because passing a SortedMap to the ConcurrentSkipListMap constructor is the only way
 * to invoke its private buildFromSorted method and avoid worst-case behavior of CSLM.put.
 */
public class ColumnSortedMap implements SortedMap<ByteBuffer, IColumn>
{
    private ColumnSerializer serializer;
    private DataInput dis;
    private Comparator<ByteBuffer> comparator;
    private int length;

    public ColumnSortedMap(Comparator<ByteBuffer> comparator, ColumnSerializer serializer, DataInput dis, int length)
    {
        this.comparator = comparator;
        this.serializer = serializer;
        this.dis = dis;
        this.length = length;
    }

    public int size()
    {
        return length;
    }

    public boolean isEmpty()
    {
        throw new UnsupportedOperationException();
    }

    public boolean containsKey(Object key)
    {
        throw new UnsupportedOperationException();
    }

    public boolean containsValue(Object value)
    {
        throw new UnsupportedOperationException();
    }

    public IColumn get(Object key)
    {
        throw new UnsupportedOperationException();
    }

    public IColumn put(ByteBuffer key, IColumn value)
    {
        throw new UnsupportedOperationException();
    }

    public IColumn remove(Object key)
    {
        throw new UnsupportedOperationException();
    }

    public void putAll(Map<? extends ByteBuffer, ? extends IColumn> m)
    {
        throw new UnsupportedOperationException();
    }

    public void clear()
    {

    }

    public Comparator<? super ByteBuffer> comparator()
    {
        return comparator;
    }

    public SortedMap<ByteBuffer, IColumn> subMap(ByteBuffer fromKey, ByteBuffer toKey)
    {
        throw new UnsupportedOperationException();
    }

    public SortedMap<ByteBuffer, IColumn> headMap(ByteBuffer toKey)
    {
        throw new UnsupportedOperationException();
    }

    public SortedMap<ByteBuffer, IColumn> tailMap(ByteBuffer fromKey)
    {
        throw new UnsupportedOperationException();
    }

    public ByteBuffer firstKey()
    {
        throw new UnsupportedOperationException();
    }

    public ByteBuffer lastKey()
    {
        throw new UnsupportedOperationException();
    }

    public Set<ByteBuffer> keySet()
    {
        throw new UnsupportedOperationException();
    }

    public Collection<IColumn> values()
    {
        throw new UnsupportedOperationException();
    }

    public Set<Map.Entry<ByteBuffer, IColumn>> entrySet()
    {
        return new ColumnSet(serializer, dis, length);
    }
}

class ColumnSet implements Set<Map.Entry<ByteBuffer, IColumn>>
{
    private ColumnSerializer serializer;
    private DataInput dis;
    private int length;

    public ColumnSet(ColumnSerializer serializer, DataInput dis, int length)
    {
        this.serializer = serializer;
        this.dis = dis;
        this.length = length;
    }

    public int size()
    {
        return length;
    }

    public boolean isEmpty()
    {
        throw new UnsupportedOperationException();
    }

    public boolean contains(Object o)
    {
        throw new UnsupportedOperationException();
    }

    public Iterator<Entry<ByteBuffer, IColumn>> iterator()
    {
        return new ColumnIterator(serializer, dis, length);
    }

    public Object[] toArray()
    {
        throw new UnsupportedOperationException();
    }

    public <T> T[] toArray(T[] a)
    {
        throw new UnsupportedOperationException();
    }

    public boolean add(Entry<ByteBuffer, IColumn> e)
    {
        throw new UnsupportedOperationException();
    }

    public boolean remove(Object o)
    {
        throw new UnsupportedOperationException();
    }

    public boolean containsAll(Collection<?> c)
    {
        throw new UnsupportedOperationException();
    }

    public boolean addAll(Collection<? extends Entry<ByteBuffer, IColumn>> c)
    {
        throw new UnsupportedOperationException();
    }

    public boolean retainAll(Collection<?> c)
    {
        throw new UnsupportedOperationException();
    }

    public boolean removeAll(Collection<?> c)
    {
        throw new UnsupportedOperationException();
    }

    public void clear()
    {
    }
}

class ColumnIterator implements Iterator<Map.Entry<ByteBuffer, IColumn>>
{
    private ColumnSerializer serializer;
    private DataInput dis;
    private int length;
    private int count = 0;

    public ColumnIterator(ColumnSerializer serializer, DataInput dis, int length)
    {
        this.dis = dis;
        this.serializer = serializer;
        this.length = length;
    }

    private IColumn deserializeNext()
    {
        try
        {
            count++;
            return serializer.deserialize(dis);
        }
        catch (IOException e)
        {
            throw new IOError(e);
        }
    }

    public boolean hasNext()
    {
        return count < length;
    }

    public Entry<ByteBuffer, IColumn> next()
    {
        if (!hasNext())
        {
            throw new IllegalStateException("end of column iterator");
        }

        final IColumn column = deserializeNext();
        return new Entry<ByteBuffer, IColumn>()
        {
            public IColumn setValue(IColumn value)
            {
                throw new UnsupportedOperationException();
            }

            public IColumn getValue()
            {
                return column;
            }

            public ByteBuffer getKey()
            {
                return column.name();
            }
        };
    }

    public void remove()
    {
        throw new UnsupportedOperationException();
    }
}
