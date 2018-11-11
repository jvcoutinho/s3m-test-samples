/*
 * Copyright 2008-2009 LinkedIn, Inc
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package voldemort.client;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.apache.log4j.Logger;

import voldemort.VoldemortException;
import voldemort.annotations.concurrency.Threadsafe;
import voldemort.annotations.jmx.JmxGetter;
import voldemort.annotations.jmx.JmxManaged;
import voldemort.annotations.jmx.JmxOperation;
import voldemort.client.scheduler.AsyncMetadataVersionManager;
import voldemort.client.scheduler.ClientRegistryRefresher;
import voldemort.cluster.Node;
import voldemort.common.service.SchedulerService;
import voldemort.routing.RoutingStrategy;
import voldemort.serialization.Serializer;
import voldemort.store.InvalidMetadataException;
import voldemort.store.Store;
import voldemort.store.StoreCapabilityType;
import voldemort.store.metadata.MetadataStore;
import voldemort.store.system.SystemStoreConstants;
import voldemort.utils.JmxUtils;
import voldemort.utils.ManifestFileReader;
import voldemort.utils.SystemTime;
import voldemort.utils.Utils;
import voldemort.versioning.InconsistencyResolver;
import voldemort.versioning.InconsistentDataException;
import voldemort.versioning.ObsoleteVersionException;
import voldemort.versioning.VectorClock;
import voldemort.versioning.Version;
import voldemort.versioning.Versioned;

import com.google.common.collect.Maps;

/**
 * The default {@link voldemort.client.StoreClient StoreClient} implementation
 * you get back from a {@link voldemort.client.StoreClientFactory
 * StoreClientFactory}
 * 
 * 
 * @param <K> The key type
 * @param <V> The value type
 */
@Threadsafe
@JmxManaged(description = "A voldemort client")
public class DefaultStoreClient<K, V> implements StoreClient<K, V> {

    private static final int ASYNC_THREADS_COUNT = 2;
    private static final boolean ALLOW_INTERRUPT_ASYNC = true;

    private final Logger logger = Logger.getLogger(DefaultStoreClient.class);
    private final StoreClientFactory storeFactory;

    private final ClientConfig config;
    private final int metadataRefreshAttempts;
    private final String storeName;
    private final InconsistencyResolver<Versioned<V>> resolver;
    private final SystemStoreRepository sysRepository;
    private final UUID clientId;
    private volatile Store<K, V, Object> store;
    private final SchedulerService scheduler;
    private ClientInfo clientInfo;
    private String clusterXml;
    private AsyncMetadataVersionManager asyncCheckMetadata = null;

    public DefaultStoreClient(String storeName,
                              InconsistencyResolver<Versioned<V>> resolver,
                              StoreClientFactory storeFactory,
                              int maxMetadataRefreshAttempts) {
        this(storeName, resolver, storeFactory, maxMetadataRefreshAttempts, null, 0, null);
    }

    public DefaultStoreClient(String storeName,
                              InconsistencyResolver<Versioned<V>> resolver,
                              StoreClientFactory storeFactory,
                              int maxMetadataRefreshAttempts,
                              String clientContext,
                              int clientSequence,
                              ClientConfig config) {

        this.storeName = Utils.notNull(storeName);
        this.resolver = resolver;
        this.storeFactory = Utils.notNull(storeFactory);
        this.metadataRefreshAttempts = maxMetadataRefreshAttempts;
        this.clientInfo = new ClientInfo(storeName,
                                         clientContext,
                                         clientSequence,
                                         System.currentTimeMillis(),
                                         ManifestFileReader.getReleaseVersion());
        this.clientId = AbstractStoreClientFactory.generateClientId(clientInfo);
        this.config = config;
        this.sysRepository = new SystemStoreRepository();
        this.scheduler = new SchedulerService(ASYNC_THREADS_COUNT,
                                              SystemTime.INSTANCE,
                                              ALLOW_INTERRUPT_ASYNC);
        // Registering self to be able to bootstrap client dynamically via JMX
        JmxUtils.registerMbean(this,
                               JmxUtils.createObjectName(JmxUtils.getPackageName(this.getClass()),
                                                         JmxUtils.getClassName(this.getClass())
                                                                 + "." + clientContext + "."
                                                                 + storeName + "."
                                                                 + clientId.toString()));

        // Bootstrap this client
        bootStrap();

        // Initialize the background thread for checking metadata version
        if(config != null) {
            asyncCheckMetadata = scheduleMetadataChecker(clientId.toString(),
                                                         config.getAsyncCheckMetadataInterval());
        }

        registerClient(clientId.toString(), config.getClientRegistryRefreshInterval());
        logger.info("Voldemort client created: " + clientId.toString() + "\n" + clientInfo);
    }

    private void registerClient(String jobId, int interval) {
        SystemStore<String, ClientInfo> clientRegistry = this.sysRepository.getClientRegistryStore();
        if(null != clientRegistry) {
            try {
                Version version = clientRegistry.putSysStore(clientId.toString(), clientInfo);
                ClientRegistryRefresher refresher = new ClientRegistryRefresher(clientRegistry,
                                                                                clientId.toString(),
                                                                                clientInfo,
                                                                                version);
                GregorianCalendar cal = new GregorianCalendar();
                cal.add(Calendar.SECOND, interval);
                scheduler.schedule(jobId + refresher.getClass().getName(),
                                   refresher,
                                   cal.getTime(),
                                   interval * 1000);
                logger.info("Client registry refresher thread started, refresh frequency: "
                            + interval + " seconds");
            } catch(Exception e) {
                logger.warn("Unable to register with the cluster due to the following error:", e);
            }
        } else {
            logger.warn(SystemStoreConstants.SystemStoreName.voldsys$_client_registry.name()
                        + "not found. Unable to registry with voldemort cluster.");
        }
    }

    private AsyncMetadataVersionManager scheduleMetadataChecker(String jobId, long interval) {
        AsyncMetadataVersionManager asyncCheckMetadata = null;
        SystemStore<String, Long> versionStore = this.sysRepository.getVersionStore();
        if(versionStore == null)
            logger.warn("Metadata version system store not found. Cannot run Metadata version check thread.");
        else {

            // Create a callback for re-bootstrapping the client
            Callable<Void> rebootstrapCallback = new Callable<Void>() {

                public Void call() throws Exception {
                    bootStrap();
                    return null;
                }
            };

            asyncCheckMetadata = new AsyncMetadataVersionManager(this.sysRepository,
                                                                 rebootstrapCallback);

            // schedule the job to run every 'checkInterval' period, starting
            // now
            scheduler.schedule(jobId + asyncCheckMetadata.getClass().getName(),
                               asyncCheckMetadata,
                               new Date(),
                               interval);
            logger.info("Metadata version check thread started. Frequency = Every " + interval
                        + " ms");

        }
        return asyncCheckMetadata;
    }

    @JmxOperation(description = "bootstrap metadata from the cluster.")
    public void bootStrap() {
        logger.info("Bootstrapping metadata for store " + this.storeName);

        /*
         * Since we need cluster.xml for bootstrapping this client as well as
         * all the System stores, just fetch it once and pass it around.
         * 
         * Seems hackish since bootstrapMetadataWithRetries only exists for
         * AbstractStoreClientFactory. TODO: Think about making this part of the
         * generic interface ?
         */
        clusterXml = ((AbstractStoreClientFactory) storeFactory).bootstrapMetadataWithRetries(MetadataStore.CLUSTER_KEY);

        this.store = storeFactory.getRawStore(storeName, resolver, clientId, null, clusterXml);

        // Create system stores
        logger.info("Creating system stores for store " + this.storeName);
        this.sysRepository.createSystemStores(this.config, this.clusterXml);

        /*
         * Update to the new metadata versions (in case we got here from Invalid
         * Metadata exception). This will prevent another bootstrap via the
         * Async metadata checker
         */
        if(asyncCheckMetadata != null) {
            asyncCheckMetadata.updateMetadataVersions();
        }
    }

    public void close() {
        scheduler.stopInner();
    }

    public boolean delete(K key) {
        Versioned<V> versioned = get(key);
        if(versioned == null)
            return false;
        return delete(key, versioned.getVersion());
    }

    public boolean delete(K key, Version version) {
        for(int attempts = 0; attempts < this.metadataRefreshAttempts; attempts++) {
            try {
                return store.delete(key, version);
            } catch(InvalidMetadataException e) {
                logger.info("Received invalid metadata exception during delete [  "
                            + e.getMessage() + " ] on store '" + storeName + "'. Rebootstrapping");
                bootStrap();
            }
        }
        throw new VoldemortException(this.metadataRefreshAttempts
                                     + " metadata refresh attempts failed.");
    }

    public V getValue(K key, V defaultValue) {
        Versioned<V> versioned = get(key);
        if(versioned == null)
            return defaultValue;
        else
            return versioned.getValue();
    }

    public V getValue(K key) {
        Versioned<V> returned = get(key, null);
        if(returned == null)
            return null;
        else
            return returned.getValue();
    }

    public Versioned<V> get(K key, Versioned<V> defaultValue) {
        for(int attempts = 0; attempts < this.metadataRefreshAttempts; attempts++) {
            try {
                List<Versioned<V>> items = store.get(key, null);
                return getItemOrThrow(key, defaultValue, items);
            } catch(InvalidMetadataException e) {
                logger.info("Received invalid metadata exception during get [  " + e.getMessage()
                            + " ] on store '" + storeName + "'. Rebootstrapping");
                bootStrap();
            }
        }
        throw new VoldemortException(this.metadataRefreshAttempts
                                     + " metadata refresh attempts failed.");
    }

    public Versioned<V> get(K key, Versioned<V> defaultValue, Object transform) {
        for(int attempts = 0; attempts < this.metadataRefreshAttempts; attempts++) {
            try {
                List<Versioned<V>> items = store.get(key, transform);
                return getItemOrThrow(key, defaultValue, items);
            } catch(InvalidMetadataException e) {
                logger.info("Received invalid metadata exception during get [  " + e.getMessage()
                            + " ] on store '" + storeName + "'. Rebootstrapping");
                bootStrap();
            }
        }
        throw new VoldemortException(this.metadataRefreshAttempts
                                     + " metadata refresh attempts failed.");
    }

    private List<Version> getVersions(K key) {
        for(int attempts = 0; attempts < this.metadataRefreshAttempts; attempts++) {
            try {
                return store.getVersions(key);
            } catch(InvalidMetadataException e) {
                logger.info("Received invalid metadata exception during getVersions [  "
                            + e.getMessage() + " ] on store '" + storeName + "'. Rebootstrapping");
                bootStrap();
            }
        }
        throw new VoldemortException(this.metadataRefreshAttempts
                                     + " metadata refresh attempts failed.");
    }

    private Versioned<V> getItemOrThrow(K key, Versioned<V> defaultValue, List<Versioned<V>> items) {
        if(items.size() == 0)
            return defaultValue;
        else if(items.size() == 1)
            return items.get(0);
        else
            throw new InconsistentDataException("Unresolved versions returned from get(" + key
                                                + ") = " + items, items);
    }

    public Versioned<V> get(K key) {
        return get(key, null);
    }

    public Map<K, Versioned<V>> getAll(Iterable<K> keys) {
        Map<K, List<Versioned<V>>> items = null;
        for(int attempts = 0;; attempts++) {
            if(attempts >= this.metadataRefreshAttempts)
                throw new VoldemortException(this.metadataRefreshAttempts
                                             + " metadata refresh attempts failed.");
            try {
                items = store.getAll(keys, null);
                break;
            } catch(InvalidMetadataException e) {
                logger.info("Received invalid metadata exception during getAll [  "
                            + e.getMessage() + " ] on store '" + storeName + "'. Rebootstrapping");
                bootStrap();
            }
        }
        Map<K, Versioned<V>> result = Maps.newHashMapWithExpectedSize(items.size());

        for(Entry<K, List<Versioned<V>>> mapEntry: items.entrySet()) {
            Versioned<V> value = getItemOrThrow(mapEntry.getKey(), null, mapEntry.getValue());
            result.put(mapEntry.getKey(), value);
        }
        return result;
    }

    public Version put(K key, V value) {
        List<Version> versions = getVersions(key);
        Versioned<V> versioned;
        if(versions.isEmpty())
            versioned = Versioned.value(value, new VectorClock());
        else if(versions.size() == 1)
            versioned = Versioned.value(value, versions.get(0));
        else {
            versioned = get(key, null);
            if(versioned == null)
                versioned = Versioned.value(value, new VectorClock());
            else
                versioned.setObject(value);
        }
        return put(key, versioned);
    }

    public Version put(K key, Versioned<V> versioned, Object transform)
            throws ObsoleteVersionException {
        for(int attempts = 0; attempts < this.metadataRefreshAttempts; attempts++) {
            try {
                store.put(key, versioned, transform);
                return versioned.getVersion();
            } catch(InvalidMetadataException e) {
                logger.info("Received invalid metadata exception during put [  " + e.getMessage()
                            + " ] on store '" + storeName + "'. Rebootstrapping");
                bootStrap();
            }
        }
        throw new VoldemortException(this.metadataRefreshAttempts
                                     + " metadata refresh attempts failed.");
    }

    public boolean putIfNotObsolete(K key, Versioned<V> versioned) {
        try {
            put(key, versioned);
            return true;
        } catch(ObsoleteVersionException e) {
            return false;
        }
    }

    public Version put(K key, Versioned<V> versioned) throws ObsoleteVersionException {

        for(int attempts = 0; attempts < this.metadataRefreshAttempts; attempts++) {
            try {
                store.put(key, versioned, null);
                return versioned.getVersion();
            } catch(InvalidMetadataException e) {
                logger.info("Received invalid metadata exception during put [  " + e.getMessage()
                            + " ] on store '" + storeName + "'. Rebootstrapping");
                bootStrap();
            }
        }
        throw new VoldemortException(this.metadataRefreshAttempts
                                     + " metadata refresh attempts failed.");
    }

    public boolean applyUpdate(UpdateAction<K, V> action) {
        return applyUpdate(action, 3);
    }

    public boolean applyUpdate(UpdateAction<K, V> action, int maxTries) {
        boolean success = false;
        try {
            for(int i = 0; i < maxTries; i++) {
                try {
                    action.update(this);
                    success = true;
                    return success;
                } catch(ObsoleteVersionException e) {
                    // ignore for now
                }
            }
        } finally {
            if(!success)
                action.rollback();
        }

        // if we got here we have seen too many ObsoleteVersionExceptions
        // and have rolled back the updates
        return false;
    }

    public List<Node> getResponsibleNodes(K key) {
        RoutingStrategy strategy = (RoutingStrategy) store.getCapability(StoreCapabilityType.ROUTING_STRATEGY);
        @SuppressWarnings("unchecked")
        Serializer<K> keySerializer = (Serializer<K>) store.getCapability(StoreCapabilityType.KEY_SERIALIZER);
        return strategy.routeRequest(keySerializer.toBytes(key));
    }

    @SuppressWarnings("unused")
    private Version getVersion(K key) {
        List<Version> versions = getVersions(key);
        if(versions.size() == 0)
            return null;
        else if(versions.size() == 1)
            return versions.get(0);
        else
            throw new InconsistentDataException("Unresolved versions returned from get(" + key
                                                + ") = " + versions, versions);
    }

    public Versioned<V> get(K key, Object transforms) {
        return get(key, null, transforms);
    }

    public Map<K, Versioned<V>> getAll(Iterable<K> keys, Map<K, Object> transforms) {
        Map<K, List<Versioned<V>>> items = null;
        for(int attempts = 0;; attempts++) {
            if(attempts >= this.metadataRefreshAttempts)
                throw new VoldemortException(this.metadataRefreshAttempts
                                             + " metadata refresh attempts failed.");
            try {
                items = store.getAll(keys, transforms);
                break;
            } catch(InvalidMetadataException e) {
                logger.info("Received invalid metadata exception during getAll [  "
                            + e.getMessage() + " ] on store '" + storeName + "'. Rebootstrapping");
                bootStrap();
            }
        }
        Map<K, Versioned<V>> result = Maps.newHashMapWithExpectedSize(items.size());

        for(Entry<K, List<Versioned<V>>> mapEntry: items.entrySet()) {
            Versioned<V> value = getItemOrThrow(mapEntry.getKey(), null, mapEntry.getValue());
            result.put(mapEntry.getKey(), value);
        }
        return result;
    }

    public Version put(K key, V value, Object transforms) {
        List<Version> versions = getVersions(key);
        Versioned<V> versioned;
        if(versions.isEmpty())
            versioned = Versioned.value(value, new VectorClock());
        else if(versions.size() == 1)
            versioned = Versioned.value(value, versions.get(0));
        else {
            versioned = get(key, null);
            if(versioned == null)
                versioned = Versioned.value(value, new VectorClock());
            else
                versioned.setObject(value);
        }
        return put(key, versioned, transforms);

    }

    public UUID getClientId() {
        return clientId;
    }

    @JmxGetter(name = "getStoreMetadataVersion")
    public String getStoreMetadataVersion() {
        String result = "Current Store Metadata Version : "
                        + this.asyncCheckMetadata.getStoreMetadataVersion();
        return result;
    }

    @JmxGetter(name = "getClusterMetadataVersion")
    public String getClusterMetadataVersion() {
        String result = "Current Cluster Metadata Version : "
                        + this.asyncCheckMetadata.getClusterMetadataVersion();
        return result;
    }
}
