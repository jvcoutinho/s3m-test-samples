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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.realm.exceptions.RealmException;
import io.realm.exceptions.RealmMigrationNeededException;
import io.realm.internal.ColumnType;
import io.realm.internal.ImplicitTransaction;
import io.realm.internal.Row;
import io.realm.internal.SharedGroup;
import io.realm.internal.Table;
import io.realm.internal.android.LooperThread;


public class Realm {
    public static final String DEFAULT_REALM_NAME = "default.realm";

    private static final String TAG = "REALM";
    private static final String TABLE_PREFIX = "class_";
    private static final Set<String> validatedPaths = Collections.synchronizedSet(new HashSet<String>());
    private static final ThreadLocal<Map<String, Realm>> realmsCache = new ThreadLocal<Map<String, Realm>>() {
        @Override
        protected Map<String, Realm> initialValue() {
            return new HashMap<String, Realm>();
        }
    };

    private static SharedGroup.Durability defaultDurability = SharedGroup.Durability.FULL;
    private static boolean autoRefresh = true;

    private final int id;
    private final LooperThread looperThread = LooperThread.getInstance();
    private final SharedGroup sharedGroup;
    private final ImplicitTransaction transaction;
    private final Map<Class<?>, String> simpleClassNames = new HashMap<Class<?>, String>();
    private final Map<String, Class<?>> generatedClasses = new HashMap<String, Class<?>>();
    private final Map<Class<?>, Constructor> constructors = new HashMap<Class<?>, Constructor>();
    private final Map<Class<?>, Method> initTableMethods = new HashMap<Class<?>, Method>();
    private final Map<Class<?>, Constructor> generatedConstructors = new HashMap<Class<?>, Constructor>();
    private final List<RealmChangeListener> changeListeners = new ArrayList<RealmChangeListener>();
    private final Map<Class<?>, Table> tables = new HashMap<Class<?>, Table>();
    private static final long UNVERSIONED = -1;

    private Handler handler;

    // Package protected to be reachable by proxy classes
    static final com.google.common.collect.Table<String, String, Long> columnIndices = HashBasedTable.create();

    // The constructor in private to enforce the use of the static one
    private Realm(String absolutePath) {
        this.sharedGroup = new SharedGroup(absolutePath, true);
        this.transaction = sharedGroup.beginImplicitTransaction();
        this.id = absolutePath.hashCode();
        if (!looperThread.isAlive()) {
            looperThread.start();
        }

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        handler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (message.what == LooperThread.REALM_CHANGED) {
                    if (autoRefresh) {
                        transaction.advanceRead();
                    }
                    sendNotifications();
                }
            }
        };
        if (Looper.myLooper() == null) {
            Looper.loop();
        }
        LooperThread.handlers.put(handler, id);
    }

    @Override
    protected void finalize() throws Throwable {
        transaction.endRead();
        super.finalize();
    }

//    public static void setDefaultDurability(SharedGroup.Durability durability) {
//        defaultDurability = durability;
//    }

    public Table getTable(Class<?> clazz) {
        String simpleClassName = simpleClassNames.get(clazz);
        if (simpleClassName == null) {
            simpleClassName = clazz.getSimpleName();
            simpleClassNames.put(clazz, simpleClassName);
        }
        return transaction.getTable(TABLE_PREFIX + simpleClassName);
    }

    /**
     * Realm static constructor
     * @param context an Android context
     * @return an instance of the Realm class
     */
    public static Realm getInstance(Context context) {
        return Realm.getInstance(context, DEFAULT_REALM_NAME);
    }

    /**
     * Realm static constructor
     * @param context an Android context
     * @param fileName the name of the file to save the Realm to
     * @return an instance of the Realm class
     */
    public static Realm getInstance(Context context, String fileName) {
        return Realm.create(context.getFilesDir(), fileName);
    }

    /**
     * Realm static constructor
     * @param writableFolder absolute path to a writable directory
     * @return an instance of the Realm class
     */
    public static Realm getInstance(File writableFolder) {
        return Realm.create(writableFolder, DEFAULT_REALM_NAME);
    }

    /**
     * Realm static constructor
     * @param writableFolder absolute path to a writable directory
     * @param filename the name of the file to save the Realm to
     * @return an instance of the Realm class
     */
    public static Realm create(File writableFolder, String filename) {
        String absolutePath = new File(writableFolder, filename).getAbsolutePath();
        return createAndValidate(absolutePath, true);
    }

    private static Realm createAndValidate(String absolutePath, boolean validateSchema) {
        Map<String, Realm> realms = realmsCache.get();
        Realm realm = realms.get(absolutePath);
        if (realm == null) {
            realm = new Realm(absolutePath);
            realms.put(absolutePath, realm);
            realmsCache.set(realms);
        }
        if (validateSchema) {
            // FIXME - get rid fo validatedPaths - only validate if we don't have a cached realm
            if (!validatedPaths.contains(absolutePath)) {
                Class<?> validationClass;
                try {
                    validationClass = Class.forName("io.realm.ValidationList");
                } catch (ClassNotFoundException e) {
                    throw new RealmException("Could not find the generated ValidationList class");
                }
                Method getProxyClassesMethod;
                try {
                    getProxyClassesMethod = validationClass.getMethod("getProxyClasses");
                } catch (NoSuchMethodException e) {
                    throw new RealmException("Could not find the getProxyClasses method in the ValidationList class");
                }
                List<String> proxyClasses;
                try {
                    proxyClasses = (List<String>) getProxyClassesMethod.invoke(null);
                } catch (IllegalAccessException e) {
                    throw new RealmException("Could not execute the getProxyClasses method in the ValidationList class");
                } catch (InvocationTargetException e) {
                    throw new RealmException("An exception was thrown in the getProxyClasses method in the ValidationList class");
                }

                long version = realm.getVersion();
                try {
                    realm.beginTransaction();
                    if (version == UNVERSIONED) {
                        realm.setVersion(0);
                    }

                    for (String className : proxyClasses) {
                        String modelClassName = Iterables.getLast(Splitter.on(".").split(className));
                        String generatedClassName = "io.realm." + modelClassName + "RealmProxy";
                        Class<?> generatedClass;
                        try {
                            generatedClass = Class.forName(generatedClassName);
                        } catch (ClassNotFoundException e) {
                            throw new RealmException("Could not find the generated " + generatedClassName + " class");
                        }

                        // if not versioned, create table
                        if (version == UNVERSIONED) {
                            Method initTableMethod;
                            try {
                                initTableMethod = generatedClass.getMethod("initTable", new Class[]{ImplicitTransaction.class});
                            } catch (NoSuchMethodException e) {
                                throw new RealmException("Could not find the initTable method in the generated " + generatedClassName + " class");
                            }
                            try {
                                initTableMethod.invoke(null, realm.transaction);
                            } catch (IllegalAccessException e) {
                                throw new RealmException("Could not execute the initTable method in the " + generatedClassName + " class");
                            } catch (InvocationTargetException e) {
                                throw new RealmException("An exception was thrown in the initTable method in the " + generatedClassName + " class");
                            }
                        }

                        // validate created table
                        Method validateMethod;
                        try {
                            validateMethod = generatedClass.getMethod("validateTable", new Class[]{ImplicitTransaction.class});
                        } catch (NoSuchMethodException e) {
                            throw new RealmException("Could not find the validateTable method in the generated " + generatedClassName + " class");
                        }
                        try {
                            validateMethod.invoke(null, realm.transaction);
                        } catch (IllegalAccessException e) {
                            throw new RealmException("Could not execute the validateTable method in the " + generatedClassName + " class");
                        } catch (InvocationTargetException e) {
                            throw new RealmMigrationNeededException(e.getMessage());
                        }

                        // Populate the columnIndices table
                        Method fieldNamesMethod;
                        try {
                            fieldNamesMethod = generatedClass.getMethod("getFieldNames");
                        } catch (NoSuchMethodException e) {
                            throw new RealmException("Could not find the getFieldNames method in the generated " + generatedClassName + " class");
                        }
                        List<String> fieldNames;
                        try {
                            fieldNames = (List<String>)fieldNamesMethod.invoke(null);
                        } catch (IllegalAccessException e) {
                            throw new RealmException("Could not execute the getFieldNames method in the generated " + generatedClassName + " class");
                        } catch (InvocationTargetException e) {
                            throw new RealmException("An exception was thrown in the getFieldNames method in the generated " + generatedClassName + " class");
                        }
                        Table table = realm.transaction.getTable(TABLE_PREFIX + modelClassName);
                        for (String fieldName : fieldNames) {
                            long columnIndex = table.getColumnIndex(fieldName);
                            if (columnIndex == -1) {
                                throw new RealmMigrationNeededException("Column '" + fieldName + "' not found for type '" + modelClassName + "'");
                            }
                            columnIndices.put(modelClassName, fieldName, columnIndex);
                        }
                    }
                    validatedPaths.add(absolutePath);
                }
                finally {
                    realm.commitTransaction();
                }
            }
        }
        return realm;
    }

    // This class stores soft-references to realm objects per thread per realm file
    private static class ThreadRealm extends ThreadLocal<Map<String, Realm>> {
        @Override
        protected Map<String, Realm> initialValue() {
            return new HashMap<String, Realm>();
        }
    }

    /**
     * Instantiates and adds a new object to the realm
     * @return The new object
     * @param clazz The Class of the object to create
     */
    public <E extends RealmObject> E createObject(Class<E> clazz) {
        Table table;
        table = tables.get(clazz);
        if (table == null) {
            String simpleClassName = simpleClassNames.get(clazz);
            if (simpleClassName == null) {
                simpleClassName = clazz.getSimpleName();
                simpleClassNames.put(clazz, simpleClassName);
            }
            String generatedClassName = "io.realm." + simpleClassName + "RealmProxy";

            Class<?> generatedClass = generatedClasses.get(generatedClassName);
            if (generatedClass == null) {
                try {
                    generatedClass = Class.forName(generatedClassName);
                } catch (ClassNotFoundException e) {
                    throw new RealmException("Could not find the generated proxy class");
                }
                generatedClasses.put(generatedClassName, generatedClass);
            }

            Method method = initTableMethods.get(generatedClass);
            if (method == null) {
                try {
                    method = generatedClass.getMethod("initTable", new Class[]{ImplicitTransaction.class});
                } catch (NoSuchMethodException e) {
                    throw new RealmException("Could not find the initTable() method in generated proxy class");
                }
                initTableMethods.put(generatedClass, method);
            }

            try {
                table = (Table) method.invoke(null, transaction);
                tables.put(clazz, table);
            } catch (IllegalAccessException e) {
                throw Throwables.propagate(e); // Wrap the exception in a runtime one
            } catch (InvocationTargetException e) {
                throw Throwables.propagate(e); // Wrap the exception in a runtime one
            }
        }

        long rowIndex = table.addEmptyRow();
        return get(clazz, rowIndex);
    }

    <E> void remove(Class<E> clazz, long objectIndex) {
        getTable(clazz).moveLastOver(objectIndex);
    }

    <E extends RealmObject> E get(Class<E> clazz, long rowIndex) {
        E result;

        Table table = tables.get(clazz);
        if (table == null) {
            String simpleClassName = simpleClassNames.get(clazz);
            if (simpleClassName == null) {
                simpleClassName = clazz.getSimpleName();
                simpleClassNames.put(clazz, simpleClassName);
            }

            table = transaction.getTable(TABLE_PREFIX + simpleClassName);
            tables.put(clazz, table);
        }

        Row row = table.getRow(rowIndex);

        Constructor constructor = generatedConstructors.get(clazz);
        if (constructor == null) {
            String simpleClassName = simpleClassNames.get(clazz);
            if (simpleClassName == null) {
                simpleClassName = clazz.getSimpleName();
                simpleClassNames.put(clazz, simpleClassName);
            }
            String generatedClassName = "io.realm." + simpleClassName + "RealmProxy";


            Class<?> generatedClass = generatedClasses.get(generatedClassName);
            if (generatedClass == null) {
                try {
                    generatedClass = Class.forName(generatedClassName);
                } catch (ClassNotFoundException e) {
                    throw new RealmException("Could not find the generated proxy class");
                }
                generatedClasses.put(generatedClassName, generatedClass);
            }

            constructor = constructors.get(generatedClass);
            if (constructor == null) {
                try {
                    constructor = generatedClass.getConstructor();
                } catch (NoSuchMethodException e) {
                    throw new RealmException("Could not find the constructor in generated proxy class");
                }
                constructors.put(generatedClass, constructor);
                generatedConstructors.put(clazz, constructor);
            }
        }

        try {
            // We are know the casted type since we generated the class
            //noinspection unchecked
            result = (E) constructor.newInstance();
        } catch (InstantiationException e) {
            throw Throwables.propagate(e); // Wrap the exception in a runtime one
        } catch (IllegalAccessException e) {
            throw Throwables.propagate(e); // Wrap the exception in a runtime one
        } catch (InvocationTargetException e) {
            throw Throwables.propagate(e); // Wrap the exception in a runtime one
        }
        result.realmSetRow(row);
        result.setRealm(this);
        return result;
    }

    boolean contains(Class<?> clazz) {
        String simpleClassName = simpleClassNames.get(clazz);
        if (simpleClassName == null) {
            simpleClassName = clazz.getSimpleName();
            simpleClassNames.put(clazz, simpleClassName);
        }
        return transaction.hasTable(simpleClassName);
    }

    /**
     * Returns a typed RealmQuery, which can be used to query for specific objects of this type
     * @param clazz The class of the object which is to be queried for
     * @return A typed RealmQuery, which can be used to query for specific objects of this type
     */
    public <E extends RealmObject> RealmQuery<E> where(Class<E> clazz) {
        return new RealmQuery<E>(this, clazz);
    }

    /**
     * Get all objects of a specific Class
     * @param clazz the Class to get objects of
     * @return A RealmResult list containing the objects
     */
    public <E extends RealmObject> RealmResults<E> allObjects(Class<E> clazz) {
        return where(clazz).findAll();
    }

    // Notifications
    public void addChangeListener(RealmChangeListener listener) {
        changeListeners.add(listener);
        LooperThread.handlers.put(handler, id);
    }

    public void removeChangeListener(RealmChangeListener listener) {
        changeListeners.remove(listener);
        if (changeListeners.isEmpty()) {
            LooperThread.handlers.remove(handler);
        }
    }

    public void removeAllChangeListeners() {
        changeListeners.clear();
        LooperThread.handlers.remove(handler);
    }

    void sendNotifications() {
        for(RealmChangeListener listener : changeListeners) {
            listener.onChange();
        }
    }

    boolean hasChanged() {
        return sharedGroup.hasChanged();
    }

    /**
     * Transactions
     */

//    public void refresh() {
//        transaction.advanceRead();
//    }

    /**
     * Starts a write transaction, this must be closed with commitTransaction()
     */
    public void beginTransaction() {
        transaction.promoteToWrite();
    }

    /**
     * Commits a write transaction
     */
    public void commitTransaction() {
        transaction.commitAndContinueAsRead();

        Message message = Message.obtain();
        message.arg1 = LooperThread.REALM_CHANGED;
        message.arg2 = id;
        if (looperThread.handler != null) {
            looperThread.handler.sendMessage(message);
        } else {
            Log.i(TAG, "The LooperThread is not up and running yet. Commit message not sent");
        }
    }

    public void clear(Class<?> classSpec) {
        getTable(classSpec).clear();
    }

    private long getVersion() {
        if (!transaction.hasTable("metadata")) {
            return UNVERSIONED;
        }
        Table metadataTable = transaction.getTable("metadata");
        return metadataTable.getLong(0, 0);
    }

    private void setVersion(long version) {
        Table metadataTable = transaction.getTable("metadata");
        if (metadataTable.getColumnCount() == 0) {
            metadataTable.addColumn(ColumnType.INTEGER, "version");
            metadataTable.addEmptyRow();
        }
        metadataTable.setLong(0, 0, version);
    }

    static public void migrateRealmAtPath(String realmPath, RealmMigration migration) {
        Realm realm = Realm.createAndValidate(realmPath, false);
        realm.beginTransaction();
        realm.setVersion(migration.execute(realm, realm.getVersion()));
        realm.commitTransaction();
        // Update the cache
        Map<String, Realm> realms = realmsCache.get();
        realms.put(realmPath, new Realm(realmPath));
        realmsCache.set(realms);
    }

    /**
     * Delete the Realm file from the filesystem for the default Realm (named "default.realm").
     * The realm must be unused and closed before calling this method.
     * @param context an Android context.
     * @return false if a file could not be deleted. The failing file will be logged.
     */
    public static boolean deleteRealmFile(Context context) {
        return deleteRealmFile(context, DEFAULT_REALM_NAME);
    }

    /**
     * Delete the Realm file from the filesystem for a custom named Realm.
     * The realm must be unused and closed before calling this method.
     * @param context an Android context.
     * @param fileName the name of the custom Realm (i.e. "myCustomRealm.realm").
     * @return false if a file could not be deleted. The failing file will be logged.
     */
    public static boolean deleteRealmFile(Context context, String fileName) {
        boolean result = true;
        File writableFolder = context.getFilesDir();
        List<File> filesToDelete = Arrays.asList(
                new File(writableFolder, fileName),
                new File(writableFolder, fileName + ".lock"));
        for (File fileToDelete : filesToDelete) {
            if (fileToDelete.exists()) {
                boolean deleteResult = fileToDelete.delete();
                if (!deleteResult) {
                    result = false;
                    Log.w(TAG, "Could not delete the file " + fileToDelete);
                }
            }
        }
        return result;
    }
}
