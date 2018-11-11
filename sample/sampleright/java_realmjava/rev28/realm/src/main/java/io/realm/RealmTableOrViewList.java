/*
 * Copyright 2014 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm;


import java.util.AbstractList;
import java.util.HashMap;
import java.util.Map;

import io.realm.internal.TableOrView;
import io.realm.internal.TableView;

/**
 *
 * @param <E> The class of objects in this list
 */
public class RealmTableOrViewList<E extends RealmObject> extends AbstractList<E> implements RealmList<E> {

    private Class<E> classSpec;
    private Realm realm;
    private TableOrView table = null;

    RealmTableOrViewList(Realm realm, Class<E> classSpec) {
        this.realm = realm;
        this.classSpec = classSpec;
    }

    RealmTableOrViewList(Realm realm, TableOrView table, Class<E> classSpec) {
        this(realm, classSpec);
        this.table = table;
    }

    Realm getRealm() {
        return realm;
    }

    TableOrView getTable() {

        if(table == null) {
            return realm.getTable(classSpec);
        } else {
            return table;
        }
    }

    @Override
    public void move(int oldPos, int newPos) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    Map<String, Class<?>> cache = new HashMap<String, Class<?>>();


    @Override
    public RealmQuery<E> where() {
        return new RealmQuery<E>(this, classSpec);
    }


    @Override
    public E get(int rowIndex) {

        E obj;

        TableOrView table = getTable();
        if(table instanceof TableView) {
            obj = realm.get(classSpec, ((TableView)table).getSourceRowIndex(rowIndex));
        } else {
            obj = realm.get(classSpec, rowIndex);
        }

        return obj;
    }

    @Override
    public E first() {
        return get(0);
    }

    @Override
    public E last() {
        return get(size()-1);
    }


    // Aggregates

    @Override
    public int size() {
        return ((Long)getTable().size()).intValue();
    }

    /**
     * Find the minimum value of a field.
     *
     * @param fieldName   The field to look for a minimum on. Only int, float, double,
     *                    and date are supported.
     * @return            An object; the first occurrence with the minimum value.
     */
    public E min(String fieldName) {
        throw new NoSuchMethodError();
    }


    /**
     * Find the maximum value of a field.
     *
     * @param fieldName   The field to look for a maximum on. Only int, float, double,
     *                    and date are supported.
     * @return            An object; the first occurrence with the maximum value.
     */
    public E max(String fieldName) {
        throw new NoSuchMethodError();
    }


    /**
     * Calculate the sum of a field.
     *
     * @param fieldName   The field to sum. Only int, float, and double are supported.
     * @return            The sum.
     */

    public Number sum(String fieldName) {
        long columnIndex = table.getColumnIndex(fieldName);
        switch (table.getColumnType(columnIndex)) {
            case INTEGER:
                return new Long(table.sumLong(columnIndex));
            case FLOAT:
                return new Float(table.sumFloat(columnIndex));
            case DOUBLE:
                return new Double(table.sumDouble(columnIndex));
            default:
                throw new RuntimeException("Wrong type");
        }
    }


    /**
     * Returns the average of a given field for objects in a RealmList.
     *
     * @param fieldName  The field to calculate average on. Only properties of type int,
     *                   float and double are supported.
     * @return           The average for the given field amongst objects in an RealmList. This
     *                   will be of type double for both float and double field.
     */
    public double average(String fieldName) {
        long columnIndex = table.getColumnIndex(fieldName);
        switch (table.getColumnType(columnIndex)) {
            case INTEGER:
                return table.averageLong(columnIndex);
            case DOUBLE:
                return table.averageDouble(columnIndex);
            case FLOAT:
                return table.averageFloat(columnIndex);
            default:
                throw new RuntimeException("Wrong type");
        }
    }
}
