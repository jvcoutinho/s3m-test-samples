/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.core.config.impl;

import static com.google.common.collect.Sets.newHashSet;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.opengamma.core.change.AggregatingChangeManager;
import com.opengamma.core.change.ChangeManager;
import com.opengamma.core.change.PassthroughChangeManager;
import com.opengamma.core.config.ConfigSource;
import com.opengamma.id.ObjectId;
import com.opengamma.id.UniqueId;
import com.opengamma.id.UniqueIdSchemeDelegator;
import com.opengamma.id.VersionCorrection;
import com.opengamma.util.ArgumentChecker;

/**
 * A source of positions that uses the scheme of the unique identifier to determine which
 * underlying source should handle the request.
 * <p>
 * If no scheme-specific handler has been registered, a default is used.
 * <p>
 * Change events are aggregated from the different sources and presented through a single change manager.
 */
public class DelegatingConfigSource
    extends UniqueIdSchemeDelegator<ConfigSource>
    implements ConfigSource {

  /**
   * Creates an instance specifying the default delegate.
   *
   * @param defaultSource  the source to use when no scheme matches, not null
   */
  public DelegatingConfigSource(final ConfigSource defaultSource) {
    super(defaultSource);
  }

  /**
   * Creates an instance specifying the default delegate.
   *
   * @param defaultSource  the source to use when no scheme matches, not null
   * @param schemePrefixToSourceMap  the map of sources by scheme to switch on, not null
   */
  public DelegatingConfigSource(final ConfigSource defaultSource, final Map<String, ConfigSource> schemePrefixToSourceMap) {
    super(defaultSource, schemePrefixToSourceMap);
    final AggregatingChangeManager changeManager = new AggregatingChangeManager();

    // REVIEW jonathan 2011-08-03 -- this assumes that the delegating source lasts for the lifetime of the engine as we
    // never detach from the underlying change managers.
    changeManager.addChangeManager(defaultSource.changeManager());
    for (final ConfigSource source : schemePrefixToSourceMap.values()) {
      changeManager.addChangeManager(source.changeManager());
    }
  }

  //-------------------------------------------------------------------------
  @Override
  public ConfigItem<?> get(final UniqueId uniqueId) {
    ArgumentChecker.notNull(uniqueId, "uniqueId");
    return chooseDelegate(uniqueId.getScheme()).get(uniqueId);
  }

  @Override
  public ConfigItem<?> get(final ObjectId objectId, final VersionCorrection versionCorrection) {
    ArgumentChecker.notNull(objectId, "objectId");
    ArgumentChecker.notNull(versionCorrection, "versionCorrection");
    return chooseDelegate(objectId.getScheme()).get(objectId, versionCorrection);
  }

  @Override
  public <R> ConfigItem<R> get(final Class<R> clazz, final String configName, final VersionCorrection versionCorrection) {
    final Map<String, ConfigSource> delegates = getDelegates();
    ArgumentChecker.notNull(clazz, "clazz");
    ArgumentChecker.notNull(configName, "configName");
    for (final ConfigSource configSource : delegates.values()) {
      final ConfigItem<R> config = configSource.get(clazz, configName, versionCorrection);
      if (config != null) {
        return config;
      }
    }
    return getDefaultDelegate().get(clazz, configName, versionCorrection);
  }

  @Override
  public <R> Collection<ConfigItem<R>> getAll(final Class<R> clazz, final VersionCorrection versionCorrection) {
    final Map<String, ConfigSource> delegates = getDelegates();
    ArgumentChecker.notNull(clazz, "clazz");
    ArgumentChecker.notNull(versionCorrection, "versionCorrection");
    final Set<ConfigItem<R>> combined = newHashSet();
    for (final ConfigSource configSource : delegates.values()) {
      final Collection<ConfigItem<R>> configs = configSource.getAll(clazz, versionCorrection);
      if (configs != null) {
        combined.addAll(configs);
      }
    }
    combined.addAll(getDefaultDelegate().getAll(clazz, versionCorrection));
    return combined;
  }

  @Override
  public <R> R getConfig(final Class<R> clazz, final UniqueId uniqueId) {
    ArgumentChecker.notNull(clazz, "clazz");
    ArgumentChecker.notNull(uniqueId, "uniqueId");
    return chooseDelegate(uniqueId.getScheme()).getConfig(clazz, uniqueId);
  }

  @Override
  public <R> R getConfig(final Class<R> clazz, final ObjectId objectId, final VersionCorrection versionCorrection) {
    ArgumentChecker.notNull(clazz, "clazz");
    ArgumentChecker.notNull(objectId, "objectId");
    ArgumentChecker.notNull(versionCorrection, "versionCorrection");
    return chooseDelegate(objectId.getScheme()).getConfig(clazz, objectId, versionCorrection);
  }

  @Override
  public <R> R getConfig(final Class<R> clazz, final String configName, final VersionCorrection versionCorrection) {
    ArgumentChecker.notNull(clazz, "clazz");
    ArgumentChecker.notNull(configName, "configName");
    ArgumentChecker.notNull(versionCorrection, "versionCorrection");

    final Map<String, ConfigSource> delegates = getDelegates();
    for (final ConfigSource configSource : delegates.values()) {
      final R config = configSource.getConfig(clazz, configName, versionCorrection);
      if (config != null) {
        return config;
      }
    }
    return getDefaultDelegate().getConfig(clazz, configName, versionCorrection);
  }

  @Override
  public <R> R getLatestByName(final Class<R> clazz, final String name) {
    return getConfig(clazz, name, VersionCorrection.LATEST);
  }

  @Override
  public ChangeManager changeManager() {
    final PassthroughChangeManager cm = new PassthroughChangeManager(getDelegates().values());
    cm.addChangeManager(getDefaultDelegate().changeManager());
    return cm;
  }

  @Override
  public Map<UniqueId, ConfigItem<?>> get(final Collection<UniqueId> uniqueIds) {
    ArgumentChecker.notNull(uniqueIds, "uniqueIds");
    final Map<UniqueId, ConfigItem<?>> map = new HashMap<UniqueId, ConfigItem<?>>();
    for (final UniqueId uniqueId : uniqueIds) {
      map.put(uniqueId, get(uniqueId));
    }
    return map;
  }

}
