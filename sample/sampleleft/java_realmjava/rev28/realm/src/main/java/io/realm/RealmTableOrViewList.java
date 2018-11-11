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



    // Sorting
    public static enum Order {
        ASCENDING, DESCENDING
    }

    /**
     * Get a sorted (ASCENDING) RealmList from an existing RealmList.
     *
     * @param fieldName  The field name to sort by.
     * @return           A sorted RealmList
     */
    public RealmList<E> sort(String fieldName) {
        return sort(fieldName, Order.ASCENDING);
    }

    /**
     * Get a sorted RealmList from an existing RealmList.
     *
     * @param fieldName  The field name to sort by.
     * @param sortOrder  The direction to sort by.
     * @return           A sorted RealmList.
     */
    public RealmList<E> sort(String fieldName, Order sortOrder) {
        throw new NoSuchMethodError();
    }


    // Deleting
    /**
     * Removes an object at a given index.
     *
     * @param index      The array index identifying the object to be removed.
     * @return           Always return null.
     */
    @Override
    public E remove(int index) {
        TableOrView table = getTable();
        table.remove(index);
        return null;
    }

    /**
     * Removes the last object in a RealmList.
     *
     */
    public void removeLast() {
        TableOrView table = getTable();
        table.removeLast();
    }

    /**
     * Removes all objects from a RealmList.
     *
     */
    public void clear() {
        TableOrView table = getTable();
        table.clear();
    }

    // Adding objects

    /**
     * Add an object.
     *
     * @param element    The object to add.
     * @return           true if object was added.
     */
    @Override
    public boolean add(E element) {
        throw new NoSuchMethodError();
    }

    /**
     * Add an object
     *
     * @param index        The array index to add the object at.
     * @param element      The object to add.
     */
    public void add(int index, E element) {
        throw new NoSuchMethodError();
    }

    /**
     * Replaces an object at the given index with a new object.
     *
     * @param index       The array index of the object to be replaced.
     * @param element     An object.
     */
    public void replace(int index, E element) {
        throw new NoSuchMethodError();
    }
}
