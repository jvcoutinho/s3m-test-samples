/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan;

import org.infinispan.batch.BatchContainer;
import org.infinispan.commands.CommandsFactory;
import org.infinispan.commands.control.LockControlCommand;
import org.infinispan.commands.read.EntrySetCommand;
import org.infinispan.commands.read.GetKeyValueCommand;
import org.infinispan.commands.read.KeySetCommand;
import org.infinispan.commands.read.SizeCommand;
import org.infinispan.commands.read.ValuesCommand;
import org.infinispan.commands.write.ClearCommand;
import org.infinispan.commands.write.EvictCommand;
import org.infinispan.commands.write.PutKeyValueCommand;
import org.infinispan.commands.write.PutMapCommand;
import org.infinispan.commands.write.RemoveCommand;
import org.infinispan.commands.write.ReplaceCommand;
import org.infinispan.config.Configuration;
import org.infinispan.config.Configuration.CacheMode;
import org.infinispan.config.ConfigurationException;
import org.infinispan.container.DataContainer;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.context.Flag;
import org.infinispan.context.InvocationContext;
import org.infinispan.context.InvocationContextContainer;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.eviction.EvictionManager;
import org.infinispan.factories.ComponentRegistry;
import org.infinispan.factories.annotations.ComponentName;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.factories.annotations.SurvivesRestarts;
import org.infinispan.interceptors.InterceptorChain;
import org.infinispan.interceptors.base.CommandInterceptor;
import org.infinispan.jmx.annotations.MBean;
import org.infinispan.jmx.annotations.ManagedAttribute;
import org.infinispan.jmx.annotations.ManagedOperation;
import org.infinispan.lifecycle.ComponentStatus;
import org.infinispan.manager.CacheContainer;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.marshall.MarshalledValue;
import org.infinispan.marshall.StreamingMarshaller;
import org.infinispan.notifications.cachelistener.CacheNotifier;
import org.infinispan.remoting.responses.ResponseGenerator;
import org.infinispan.remoting.rpc.RpcManager;
import org.infinispan.statetransfer.StateTransferManager;
import org.infinispan.stats.Stats;
import org.infinispan.stats.StatsImpl;
import org.infinispan.transaction.TransactionCoordinator;
import org.infinispan.transaction.TransactionTable;
import org.infinispan.transaction.xa.TransactionXaAdapter;
import org.infinispan.transaction.xa.recovery.RecoveryManager;
import org.infinispan.util.Util;
import org.infinispan.util.concurrent.AbstractInProcessNotifyingFuture;
import org.infinispan.util.concurrent.DeferredReturnFuture;
import org.infinispan.util.concurrent.NotifyingFuture;
import org.infinispan.util.concurrent.NotifyingNotifiableFuture;
import org.infinispan.util.concurrent.locks.LockManager;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;
import org.rhq.helpers.pluginAnnotations.agent.DataType;
import org.rhq.helpers.pluginAnnotations.agent.DisplayType;
import org.rhq.helpers.pluginAnnotations.agent.Metric;
import org.rhq.helpers.pluginAnnotations.agent.Operation;

import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAResource;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.infinispan.context.Flag.*;
import static org.infinispan.factories.KnownComponentNames.*;

/**
 * @author Mircea.Markus@jboss.com
 * @author Galder Zamarreño
 * @author Sanne Grinovero
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @since 4.0
 */
@SurvivesRestarts
@MBean(objectName = CacheImpl.OBJECT_NAME, description = "Component that represents an individual cache instance.")
public class CacheImpl<K, V> extends CacheSupport<K, V> implements AdvancedCache<K, V> {
   public static final String OBJECT_NAME = "Cache";
   protected InvocationContextContainer icc;
   protected CommandsFactory commandsFactory;
   protected InterceptorChain invoker;
   protected Configuration config;
   protected CacheNotifier notifier;
   protected BatchContainer batchContainer;
   protected ComponentRegistry componentRegistry;
   protected TransactionManager transactionManager;
   protected RpcManager rpcManager;
   protected StreamingMarshaller marshaller;
   private final String name;
   private EvictionManager evictionManager;
   private DataContainer dataContainer;
   private static final Log log = LogFactory.getLog(CacheImpl.class);
   private EmbeddedCacheManager cacheManager;
   // this is never used here but should be injected - this is a hack to make sure the StateTransferManager is properly constructed if needed.
   private StateTransferManager stateTransferManager;
   // as above for ResponseGenerator
   private ResponseGenerator responseGenerator;
   private LockManager lockManager;
   private DistributionManager distributionManager;
   private ExecutorService asyncExecutor;
   private TransactionTable txTable;
   private RecoveryManager recoveryManager;
   private TransactionCoordinator txCoordinator;

   private final ThreadLocal<PreInvocationContext> preInvocationContextHolder = new ThreadLocal<PreInvocationContext>() {
      protected PreInvocationContext initialValue() {
         return new PreInvocationContext();
      }
   };

   public CacheImpl(String name) {
      this.name = name;
   }

   @Inject
   public void injectDependencies(EvictionManager evictionManager,
                                  InvocationContextContainer icc,
                                  CommandsFactory commandsFactory,
                                  InterceptorChain interceptorChain,
                                  Configuration configuration,
                                  CacheNotifier notifier,
                                  ComponentRegistry componentRegistry,
                                  TransactionManager transactionManager,
                                  BatchContainer batchContainer,
                                  RpcManager rpcManager, DataContainer dataContainer,
                                  @ComponentName(CACHE_MARSHALLER) StreamingMarshaller marshaller,
                                  ResponseGenerator responseGenerator,
                                  DistributionManager distributionManager,
                                  EmbeddedCacheManager cacheManager, StateTransferManager stateTransferManager,
                                  @ComponentName(ASYNC_TRANSPORT_EXECUTOR) ExecutorService asyncExecutor,
                                  TransactionTable txTable, RecoveryManager recoveryManager, TransactionCoordinator txCoordinator,
                                  LockManager lockManager) {
      this.commandsFactory = commandsFactory;
      this.invoker = interceptorChain;
      this.config = configuration;
      this.notifier = notifier;
      this.componentRegistry = componentRegistry;
      this.transactionManager = transactionManager;
      this.batchContainer = batchContainer;
      this.rpcManager = rpcManager;
      this.evictionManager = evictionManager;
      this.dataContainer = dataContainer;
      this.marshaller = marshaller;
      this.cacheManager = cacheManager;
      this.responseGenerator = responseGenerator;
      this.stateTransferManager = stateTransferManager;
      this.icc = icc;
      this.distributionManager = distributionManager;
      this.asyncExecutor = asyncExecutor;
      this.txTable = txTable;
      this.recoveryManager = recoveryManager;
      this.txCoordinator = txCoordinator;
      this.lockManager = lockManager;
   }

   private void assertKeyNotNull(Object key) {
      if (key == null) {
         throw new NullPointerException("Null keys are not supported!");
      }
   }

   private void assertKeysNotNull(Map<?, ?> data) {
      if (data == null) {
         throw new NullPointerException("Expected map cannot be null");
      }
      for (Object key : data.keySet()) {
         if (key == null) {
            throw new NullPointerException("Null keys are not supported!");
         }
      }
   }

   public final boolean remove(Object key, Object value) {
      return remove(key, value, null, null);
   }

   final boolean remove(Object key, Object value, EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      assertKeyNotNull(key);
      InvocationContext ctx = getInvocationContext(false, explicitFlags, explicitClassLoader);
      RemoveCommand command = commandsFactory.buildRemoveCommand(key, value, ctx.getFlags());
      return (Boolean) invoker.invoke(ctx, command);
   }

   public final int size() {
      return size(null, null);
   }

   final int size(EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      SizeCommand command = commandsFactory.buildSizeCommand();
      return (Integer) invoker.invoke(getInvocationContext(false, explicitFlags, explicitClassLoader), command);
   }

   public final boolean isEmpty() {
      return size() == 0;
   }

   final boolean isEmpty(EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      return size(explicitFlags, explicitClassLoader) == 0;
   }

   public final boolean containsKey(Object key) {
      return containsKey(key, null, null);
   }

   final boolean containsKey(Object key, EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      assertKeyNotNull(key);
      InvocationContext ctx = getInvocationContext(false, explicitFlags, explicitClassLoader);
      GetKeyValueCommand command = commandsFactory.buildGetKeyValueCommand(key, ctx.getFlags());
      Object response = invoker.invoke(ctx, command);
      return response != null;
   }

   public final boolean containsValue(Object value) {
      throw new UnsupportedOperationException("Not supported");
   }

   public final V get(Object key) {
      return get(key, null, null);
   }

   @SuppressWarnings("unchecked")
   final V get(Object key, EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      assertKeyNotNull(key);
      InvocationContext ctx = getInvocationContext(false, explicitFlags, explicitClassLoader);
      GetKeyValueCommand command = commandsFactory.buildGetKeyValueCommand(key, ctx.getFlags());
      return (V) invoker.invoke(ctx, command);
   }

   public final V remove(Object key) {
      return remove(key, null, null);
   }

   @SuppressWarnings("unchecked")
   final V remove(Object key, EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      assertKeyNotNull(key);
      InvocationContext ctx = getInvocationContext(false, explicitFlags, explicitClassLoader);
      RemoveCommand command = commandsFactory.buildRemoveCommand(key, null, ctx.getFlags());
      return (V) invoker.invoke(ctx, command);
   }

   public final void clear() {
      clear(null, null);
   }

   final void clear(EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      InvocationContext ctx = getInvocationContext(false, explicitFlags, explicitClassLoader);
      ClearCommand command = commandsFactory.buildClearCommand(ctx.getFlags());
      invoker.invoke(ctx, command);
   }

   public Set<K> keySet() {
      return keySet(null, null);
   }

   @SuppressWarnings("unchecked")
   Set<K> keySet(EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      InvocationContext ctx = getInvocationContext(false, explicitFlags, explicitClassLoader);
      KeySetCommand command = commandsFactory.buildKeySetCommand();
      return (Set<K>) invoker.invoke(ctx, command);
   }

   public Collection<V> values() {
      return values(null, null);
   }

   @SuppressWarnings("unchecked")
   Collection<V> values(EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      InvocationContext ctx = getInvocationContext(false, explicitFlags, explicitClassLoader);
      ValuesCommand command = commandsFactory.buildValuesCommand();
      return (Collection<V>) invoker.invoke(ctx, command);
   }

   public Set<Map.Entry<K, V>> entrySet() {
      return entrySet(null, null);
   }

   @SuppressWarnings("unchecked")
   Set<Map.Entry<K, V>> entrySet(EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      InvocationContext ctx = getInvocationContext(false, explicitFlags, explicitClassLoader);
      EntrySetCommand command = commandsFactory.buildEntrySetCommand();
      return (Set<Map.Entry<K, V>>) invoker.invoke(ctx, command);
   }

   public final void putForExternalRead(K key, V value) {
      putForExternalRead(key, value, null, null);
   }

   final void putForExternalRead(K key, V value, EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      Transaction ongoingTransaction = null;
      try {
         ongoingTransaction = getOngoingTransaction();
         if (ongoingTransaction != null)
            transactionManager.suspend();

         Set<Flag> flags = EnumSet.of(FAIL_SILENTLY, FORCE_ASYNCHRONOUS, ZERO_LOCK_ACQUISITION_TIMEOUT, PUT_FOR_EXTERNAL_READ);
         if (explicitFlags != null && !explicitFlags.isEmpty()) {
            flags.addAll(explicitFlags);
         } else {
            // we now need to check the ThreadLocal for any flags also set with withFlag() over here.
            PreInvocationContext pic = preInvocationContextHolder.get();
            if (!pic.flags.isEmpty()) flags.addAll(pic.flags);
         }

         // if the entry exists then this should be a no-op.
         putIfAbsent(key, value, defaultLifespan, TimeUnit.MILLISECONDS, defaultMaxIdleTime, TimeUnit.MILLISECONDS, explicitFlags, explicitClassLoader);
      } catch (Exception e) {
         if (log.isDebugEnabled()) log.debug("Caught exception while doing putForExternalRead()", e);
      } finally {
         try {
            if (ongoingTransaction != null) transactionManager.resume(ongoingTransaction);
         } catch (Exception e) {
            if (log.isDebugEnabled())
               log.debug("Had problems trying to resume a transaction after putForExternalRead()", e);
         }
      }
   }

   public final void evict(K key) {
      evict(key, null, null);
   }

   final void evict(K key, EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      assertKeyNotNull(key);
      InvocationContext ctx = getInvocationContext(true, explicitFlags, explicitClassLoader);
      EvictCommand command = commandsFactory.buildEvictCommand(key);
      invoker.invoke(ctx, command);
   }

   public Configuration getConfiguration() {
      return config;
   }

   public void addListener(Object listener) {
      notifier.addListener(listener);
   }

   public void removeListener(Object listener) {
      notifier.removeListener(listener);
   }

   public Set<Object> getListeners() {
      return notifier.getListeners();
   }

   private InvocationContext getInvocationContext(boolean forceNonTransactional, EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      InvocationContext ctx = forceNonTransactional ? icc.createNonTxInvocationContext() : icc.createInvocationContext();
      return setInvocationContextFlagsAndClassLoader(ctx, explicitFlags, explicitClassLoader);
   }

   private InvocationContext getInvocationContext(Transaction tx, EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      InvocationContext ctx = icc.createInvocationContext(tx);
      return setInvocationContextFlagsAndClassLoader(ctx, explicitFlags, explicitClassLoader);
   }

   private InvocationContext setInvocationContextFlagsAndClassLoader(InvocationContext ctx, EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      PreInvocationContext pic = null;
      if (explicitFlags == null) {
         // fall back to getting potential flags from ThreadLocal
         pic = preInvocationContextHolder.get();
         if (!pic.flags.isEmpty()) ctx.setFlags(pic.flags);
      } else {
         ctx.setFlags(explicitFlags);
      }

      if (explicitClassLoader == null) {
         // Either set per-invocation, or configured classloader
         if (pic == null) pic = preInvocationContextHolder.get();
         ClassLoader cl = pic.classLoader != null ? pic.classLoader : getClassLoader();
         ctx.setClassLoader(cl);
      } else {
         ctx.setClassLoader(explicitClassLoader);
      }

      if (pic != null) pic.reset();
      return ctx;
   }

   public boolean lock(K... keys) {
      assertKeyNotNull(keys);
      return lock(Arrays.asList(keys), null, null);
   }

   public boolean lock(Collection<? extends K> keys) {
      return lock(keys, null, null);
   }

   boolean lock(Collection<? extends K> keys, EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      if (keys == null || keys.isEmpty()) {
         throw new IllegalArgumentException("Cannot lock empty list of keys");
      }
      InvocationContext ctx = getInvocationContext(false, explicitFlags, explicitClassLoader);
      LockControlCommand command = commandsFactory.buildLockControlCommand(keys, false, ctx.getFlags());
      return (Boolean) invoker.invoke(ctx, command);
   }

   @ManagedOperation(description = "Starts the cache.")
   @Operation(displayName = "Starts cache.")
   public void start() {
      componentRegistry.start();
      defaultLifespan = config.getExpirationLifespan();
      defaultMaxIdleTime = config.getExpirationMaxIdle();
      if (log.isDebugEnabled()) log.debugf("Started cache %s on %s", getName(), getCacheManager().getAddress());
   }

   @ManagedOperation(description = "Stops the cache.")
   @Operation(displayName = "Stops cache.")
   public void stop() {
      stop(null, null);
   }

   void stop(EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      if (log.isDebugEnabled()) log.debugf("Stopping cache %s on %s", getName(), getCacheManager().getAddress());

      // Create invocation context to pass flags
      getInvocationContext(false, explicitFlags, explicitClassLoader);
      componentRegistry.stop();
   }

   public List<CommandInterceptor> getInterceptorChain() {
      return invoker.asList();
   }

   public void addInterceptor(CommandInterceptor i, int position) {
      invoker.addInterceptor(i, position);
   }

   public void addInterceptorAfter(CommandInterceptor i, Class<? extends CommandInterceptor> afterInterceptor) {
      invoker.addInterceptorAfter(i, afterInterceptor);
   }

   public void addInterceptorBefore(CommandInterceptor i, Class<? extends CommandInterceptor> beforeInterceptor) {
      invoker.addInterceptorBefore(i, beforeInterceptor);
   }

   public void removeInterceptor(int position) {
      invoker.removeInterceptor(position);
   }

   public void removeInterceptor(Class<? extends CommandInterceptor> interceptorType) {
      invoker.removeInterceptor(interceptorType);
   }

   public EvictionManager getEvictionManager() {
      return evictionManager;
   }

   public ComponentRegistry getComponentRegistry() {
      return componentRegistry;
   }

   public DistributionManager getDistributionManager() {
      return distributionManager;
   }

   public ComponentStatus getStatus() {
      return componentRegistry.getStatus();
   }

   /**
    * Returns String representation of ComponentStatus enumeration in order to avoid class not found exceptions in JMX
    * tools that don't have access to infinispan classes.
    */
   @ManagedAttribute(description = "Returns the cache status")
   @Metric(displayName = "Cache status", dataType = DataType.TRAIT, displayType = DisplayType.SUMMARY)
   public String getCacheStatus() {
      return getStatus().toString();
   }

   public boolean startBatch() {
      if (!config.isInvocationBatchingEnabled()) {
         throw new ConfigurationException("Invocation batching not enabled in current configuration!  Please use the <invocationBatching /> element.");
      }
      return batchContainer.startBatch();
   }

   public void endBatch(boolean successful) {
      if (!config.isInvocationBatchingEnabled()) {
         throw new ConfigurationException("Invocation batching not enabled in current configuration!  Please use the <invocationBatching /> element.");
      }
      batchContainer.endBatch(successful);
   }

   public String getName() {
      return name;
   }

   /**
    * Returns the cache name. If this is the default cache, it returns a more friendly name.
    */
   @ManagedAttribute(description = "Returns the cache name")
   @Metric(displayName = "Cache name", dataType = DataType.TRAIT, displayType = DisplayType.SUMMARY)
   public String getCacheName() {
      String name = getName().equals(CacheContainer.DEFAULT_CACHE_NAME) ? "Default Cache" : getName();
      return name + "(" + getConfiguration().getCacheModeString().toLowerCase() + ")";
   }

   /**
    * Returns the cache configuration as XML string.
    */
   @ManagedAttribute(description = "Returns the cache configuration as XML string")
   @Metric(displayName = "Cache configuration (XML)", dataType = DataType.TRAIT, displayType = DisplayType.SUMMARY)
   public String getConfigurationAsXmlString() {
      return getConfiguration().toXmlString();
   }

   public String getVersion() {
      return Version.VERSION;
   }

   @Override
   public String toString() {
      return "Cache '" + name + "'@" + (config.getCacheMode().isClustered() ? getCacheManager().getAddress() : Util.hexIdHashCode(this));
   }

   public BatchContainer getBatchContainer() {
      return batchContainer;
   }

   public InvocationContextContainer getInvocationContextContainer() {
      return icc;
   }

   public DataContainer getDataContainer() {
      return dataContainer;
   }

   public TransactionManager getTransactionManager() {
      return transactionManager;
   }

   public LockManager getLockManager() {
      return this.lockManager;
   }

   public EmbeddedCacheManager getCacheManager() {
      return cacheManager;
   }

   public Stats getStats() {
      return new StatsImpl(invoker);
   }

   @Override
   public XAResource getXAResource() {
      return new TransactionXaAdapter(null, txTable, config, recoveryManager, txCoordinator);
   }

   public final V put(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdleTime, TimeUnit idleTimeUnit) {
      return put(key, value, lifespan, lifespanUnit, maxIdleTime, idleTimeUnit, null, null);
   }

   @SuppressWarnings("unchecked")
   final V put(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdleTime, TimeUnit idleTimeUnit, EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      assertKeyNotNull(key);
      InvocationContext ctx = getInvocationContext(false, explicitFlags, explicitClassLoader);
      PutKeyValueCommand command = commandsFactory.buildPutKeyValueCommand(key, value, lifespanUnit.toMillis(lifespan), idleTimeUnit.toMillis(maxIdleTime), ctx.getFlags());
      return (V) invoker.invoke(ctx, command);
   }

   public final V putIfAbsent(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdleTime, TimeUnit idleTimeUnit) {
      return putIfAbsent(key, value, lifespan, lifespanUnit, maxIdleTime, idleTimeUnit, null, null);
   }

   @SuppressWarnings("unchecked")
   final V putIfAbsent(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdleTime, TimeUnit idleTimeUnit, EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      assertKeyNotNull(key);
      InvocationContext ctx = getInvocationContext(false, explicitFlags, explicitClassLoader);
      PutKeyValueCommand command = commandsFactory.buildPutKeyValueCommand(key, value, lifespanUnit.toMillis(lifespan), idleTimeUnit.toMillis(maxIdleTime), ctx.getFlags());
      command.setPutIfAbsent(true);
      return (V) invoker.invoke(ctx, command);
   }

   public final void putAll(Map<? extends K, ? extends V> map, long lifespan, TimeUnit lifespanUnit, long maxIdleTime, TimeUnit idleTimeUnit) {
      putAll(map, lifespan, lifespanUnit, maxIdleTime, idleTimeUnit, null, null);
   }

   final void putAll(Map<? extends K, ? extends V> map, long lifespan, TimeUnit lifespanUnit, long maxIdleTime, TimeUnit idleTimeUnit, EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      assertKeysNotNull(map);
      InvocationContext ctx = getInvocationContext(false, explicitFlags, explicitClassLoader);
      PutMapCommand command = commandsFactory.buildPutMapCommand(map, lifespanUnit.toMillis(lifespan), idleTimeUnit.toMillis(maxIdleTime), ctx.getFlags());
      invoker.invoke(ctx, command);
   }

   public final V replace(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdleTime, TimeUnit idleTimeUnit) {
      return replace(key, value, lifespan, lifespanUnit, maxIdleTime, idleTimeUnit, null, null);
   }

   @SuppressWarnings("unchecked")
   final V replace(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdleTime, TimeUnit idleTimeUnit, EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      assertKeyNotNull(key);
      InvocationContext ctx = getInvocationContext(false, explicitFlags, explicitClassLoader);
      ReplaceCommand command = commandsFactory.buildReplaceCommand(key, null, value, lifespanUnit.toMillis(lifespan), idleTimeUnit.toMillis(maxIdleTime), ctx.getFlags());
      return (V) invoker.invoke(ctx, command);

   }

   public final boolean replace(K key, V oldValue, V value, long lifespan, TimeUnit lifespanUnit, long maxIdleTime, TimeUnit idleTimeUnit) {
      return replace(key, oldValue, value, lifespan, lifespanUnit, maxIdleTime, idleTimeUnit, null, null);
   }

   final boolean replace(K key, V oldValue, V value, long lifespan, TimeUnit lifespanUnit, long maxIdleTime, TimeUnit idleTimeUnit, EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      assertKeyNotNull(key);
      InvocationContext ctx = getInvocationContext(false, explicitFlags, explicitClassLoader);
      ReplaceCommand command = commandsFactory.buildReplaceCommand(key, oldValue, value, lifespanUnit.toMillis(lifespan), idleTimeUnit.toMillis(maxIdleTime), ctx.getFlags());
      return (Boolean) invoker.invoke(ctx, command);
   }

   /**
    * Wraps a return value as a future, if needed.  Typically, if the stack, operation and configuration support
    * handling of futures, this retval is already a future in which case this method does nothing except cast to
    * future.
    * <p/>
    * Otherwise, a future wrapper is created, which has already completed and simply returns the retval.  This is used
    * for API consistency.
    *
    * @param retval return value to wrap
    * @param <X>    contents of the future
    * @return a future
    */
   @SuppressWarnings("unchecked")
   private <X> NotifyingFuture<X> wrapInFuture(final Object retval) {
      if (retval instanceof NotifyingFuture) {
         return (NotifyingFuture<X>) retval;
      } else {
         return new AbstractInProcessNotifyingFuture<X>() {
            @SuppressWarnings("unchecked")
            public X get() throws InterruptedException, ExecutionException {
               return (X) retval;
            }
         };
      }
   }

   public final NotifyingFuture<V> putAsync(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdle, TimeUnit maxIdleUnit) {
      return putAsync(key, value, lifespan, lifespanUnit, maxIdle, maxIdleUnit, null, null);
   }

   final NotifyingFuture<V> putAsync(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdle, TimeUnit maxIdleUnit, EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      assertKeyNotNull(key);
      InvocationContext ctx = getInvocationContext(false, explicitFlags, explicitClassLoader);
      ctx.setUseFutureReturnType(true);
      PutKeyValueCommand command = commandsFactory.buildPutKeyValueCommand(key, value, lifespanUnit.toMillis(lifespan), maxIdleUnit.toMillis(maxIdle), ctx.getFlags());
      return wrapInFuture(invoker.invoke(ctx, command));
   }

   public final NotifyingFuture<Void> putAllAsync(Map<? extends K, ? extends V> data, long lifespan, TimeUnit lifespanUnit, long maxIdle, TimeUnit maxIdleUnit) {
      return putAllAsync(data, lifespan, lifespanUnit, maxIdle, maxIdleUnit, null, null);
   }

   final NotifyingFuture<Void> putAllAsync(Map<? extends K, ? extends V> data, long lifespan, TimeUnit lifespanUnit, long maxIdle, TimeUnit maxIdleUnit, EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      assertKeysNotNull(data);
      InvocationContext ctx = getInvocationContext(false, explicitFlags, explicitClassLoader);
      ctx.setUseFutureReturnType(true);
      PutMapCommand command = commandsFactory.buildPutMapCommand(data, lifespanUnit.toMillis(lifespan), maxIdleUnit.toMillis(maxIdle), ctx.getFlags());
      return wrapInFuture(invoker.invoke(ctx, command));
   }

   public final NotifyingFuture<Void> clearAsync() {
      return clearAsync(null, null);
   }

   final NotifyingFuture<Void> clearAsync(EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      InvocationContext ctx = getInvocationContext(false, explicitFlags, explicitClassLoader);
      ctx.setUseFutureReturnType(true);
      ClearCommand command = commandsFactory.buildClearCommand(ctx.getFlags());
      return wrapInFuture(invoker.invoke(ctx, command));
   }

   public final NotifyingFuture<V> putIfAbsentAsync(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdle, TimeUnit maxIdleUnit) {
      return putIfAbsentAsync(key, value, lifespan, lifespanUnit, maxIdle, maxIdleUnit, null, null);
   }

   final NotifyingFuture<V> putIfAbsentAsync(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdle, TimeUnit maxIdleUnit, EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      assertKeyNotNull(key);
      InvocationContext ctx = getInvocationContext(false, explicitFlags, explicitClassLoader);
      ctx.setUseFutureReturnType(true);
      PutKeyValueCommand command = commandsFactory.buildPutKeyValueCommand(key, value, lifespanUnit.toMillis(lifespan), maxIdleUnit.toMillis(maxIdle), ctx.getFlags());
      command.setPutIfAbsent(true);
      return wrapInFuture(invoker.invoke(ctx, command));
   }

   public final NotifyingFuture<V> removeAsync(Object key) {
      return removeAsync(key, null, null);
   }

   final NotifyingFuture<V> removeAsync(Object key, EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      assertKeyNotNull(key);
      InvocationContext ctx = getInvocationContext(false, explicitFlags, explicitClassLoader);
      ctx.setUseFutureReturnType(true);
      RemoveCommand command = commandsFactory.buildRemoveCommand(key, null, ctx.getFlags());
      return wrapInFuture(invoker.invoke(ctx, command));
   }

   public final NotifyingFuture<Boolean> removeAsync(Object key, Object value) {
      return removeAsync(key, value, null, null);
   }

   final NotifyingFuture<Boolean> removeAsync(Object key, Object value, EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      assertKeyNotNull(key);
      InvocationContext ctx = getInvocationContext(false, explicitFlags, explicitClassLoader);
      ctx.setUseFutureReturnType(true);
      RemoveCommand command = commandsFactory.buildRemoveCommand(key, value, ctx.getFlags());
      return wrapInFuture(invoker.invoke(ctx, command));
   }

   public final NotifyingFuture<V> replaceAsync(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdle, TimeUnit maxIdleUnit) {
      return replaceAsync(key, value, lifespan, lifespanUnit, maxIdle, maxIdleUnit, null, null);
   }

   final NotifyingFuture<V> replaceAsync(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdle, TimeUnit maxIdleUnit, EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      assertKeyNotNull(key);
      InvocationContext ctx = getInvocationContext(false, explicitFlags, explicitClassLoader);
      ctx.setUseFutureReturnType(true);
      ReplaceCommand command = commandsFactory.buildReplaceCommand(key, null, value, lifespanUnit.toMillis(lifespan), maxIdleUnit.toMillis(maxIdle), ctx.getFlags());
      return wrapInFuture(invoker.invoke(ctx, command));
   }

   public final NotifyingFuture<Boolean> replaceAsync(K key, V oldValue, V newValue, long lifespan, TimeUnit lifespanUnit, long maxIdle, TimeUnit maxIdleUnit) {
      return replaceAsync(key, oldValue, newValue, lifespan, lifespanUnit, maxIdle, maxIdleUnit, null, null);
   }

   final NotifyingFuture<Boolean> replaceAsync(K key, V oldValue, V newValue, long lifespan, TimeUnit lifespanUnit, long maxIdle, TimeUnit maxIdleUnit, EnumSet<Flag> explicitFlags, ClassLoader explicitClassLoader) {
      assertKeyNotNull(key);
      InvocationContext ctx = getInvocationContext(false, explicitFlags, explicitClassLoader);
      ctx.setUseFutureReturnType(true);
      ReplaceCommand command = commandsFactory.buildReplaceCommand(key, oldValue, newValue, lifespanUnit.toMillis(lifespan), maxIdleUnit.toMillis(maxIdle), ctx.getFlags());
      return wrapInFuture(invoker.invoke(ctx, command));
   }

   public NotifyingFuture<V> getAsync(K key) {
      return getAsync(key, null, null);
   }

   @SuppressWarnings("unchecked")
   NotifyingFuture<V> getAsync(final K key, final EnumSet<Flag> explicitFlags, final ClassLoader explicitClassLoader) {
      final Transaction tx = getOngoingTransaction();
      final NotifyingNotifiableFuture f = new DeferredReturnFuture();
      EnumSet<Flag> flags = mergeFlags(explicitFlags);

      // Optimization to not start a new thread only when the operation is cheap:
      if (asyncSkipsThread(flags, key)) {
         return wrapInFuture(get(key));
      } else {
         // Make sure the flags are cleared
         final EnumSet<Flag> appliedFlags;
         if (flags == null) {
            appliedFlags = null;
         } else {
            appliedFlags = flags.clone();
            flags.clear();
         }
         Callable<V> c = new Callable<V>() {
            @Override
            public V call() throws Exception {
               assertKeyNotNull(key);
               InvocationContext ctx = getInvocationContext(tx, explicitFlags, explicitClassLoader);
               if (appliedFlags != null)
                  ctx.setFlags(appliedFlags);

               GetKeyValueCommand command = commandsFactory.buildGetKeyValueCommand(key, appliedFlags);
               Object ret = invoker.invoke(ctx, command);
               f.notifyDone();
               return (V) ret;
            }
         };
         f.setNetworkFuture(asyncExecutor.submit(c));
         return f;
      }
   }

   private EnumSet<Flag> mergeFlags(EnumSet<Flag> f) {
      if (f == null || f.isEmpty())
         return preInvocationContextHolder.get() == null ? null : preInvocationContextHolder.get().flags;
      else
         return f;
   }

   /**
    * Encodes the cases for an asyncGet operation in which it makes sense to actually perform the operation in sync.
    *
    * @param flags
    * @param key
    * @return true if we skip the thread (performing it in sync)
    */
   private boolean asyncSkipsThread(EnumSet<Flag> flags, K key) {
      boolean isSkipLoader = isSkipLoader(flags);
      if (!isSkipLoader) {
         // if we can't skip the cacheloader, we really want a thread for async.
         return false;
      }
      CacheMode cacheMode = config.getCacheMode();
      if (!cacheMode.isDistributed()) {
         //in these cluster modes we won't RPC for a get, so no need to fork a thread.
         return true;
      } else if (flags != null && (flags.contains(Flag.SKIP_REMOTE_LOOKUP) || flags.contains(Flag.CACHE_MODE_LOCAL))) {
         //with these flags we won't RPC either
         return true;
      }
      //finally, we will skip the thread if the key maps to the local node
      return distributionManager.getLocality(key).isLocal();
   }

   private boolean isSkipLoader(EnumSet<Flag> flags) {
      boolean hasCacheLoaderConfig = config.getCacheLoaderManagerConfig().getFirstCacheLoaderConfig() != null;
      return !hasCacheLoaderConfig
            || (hasCacheLoaderConfig && flags != null && (flags.contains(Flag.SKIP_CACHE_LOAD) || flags.contains(Flag.SKIP_CACHE_STORE)));
   }

   public AdvancedCache<K, V> getAdvancedCache() {
      return this;
   }

   public void compact() {
      for (InternalCacheEntry e : dataContainer) {
         if (e.getKey() instanceof MarshalledValue) {
            ((MarshalledValue) e.getKey()).compact(true, true);
         }
         if (e.getValue() instanceof MarshalledValue) {
            ((MarshalledValue) e.getValue()).compact(true, true);
         }
      }
   }

   public RpcManager getRpcManager() {
      return rpcManager;
   }

   public AdvancedCache<K, V> withFlags(Flag... flags) {
      if (flags != null && flags.length > 0) {
         PreInvocationContext pic = preInvocationContextHolder.get();
         // we will also have a valid PIC value because of initialValue()
         pic.add(flags);
      }
      return this;
   }

   private Transaction getOngoingTransaction() {
      try {
         return transactionManager != null ? transactionManager.getTransaction() : null;
      } catch (SystemException e) {
         throw new CacheException("Unable to get transaction", e);
      }
   }

   private static final class PreInvocationContext {
      private EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
      private ClassLoader classLoader;

      private PreInvocationContext() {
      }

      private void add(Flag[] newFlags) {
         for (Flag f : newFlags) {
            flags.add(f);
         }
      }

      public void reset() {
         flags.clear();
         classLoader = null;
      }

      public void setClassLoader(ClassLoader classLoader) {
         this.classLoader = classLoader;
      }
   }

   @Override
   public ClassLoader getClassLoader() {
      return config.getClassLoader();
   }

   @Override
   public AdvancedCache<K, V> with(ClassLoader classLoader) {
      if (classLoader == null)
         throw new NullPointerException("Class loader cannot be null");

      PreInvocationContext pic = preInvocationContextHolder.get();
      pic.setClassLoader(classLoader);
      return this;
   }

   @Override
   protected void set(K key, V value) {
      withFlags(Flag.SKIP_REMOTE_LOOKUP, Flag.SKIP_CACHE_LOAD)
            .put(key, value, defaultLifespan, MILLISECONDS, defaultMaxIdleTime, MILLISECONDS);
   }
}
