/**
 * Copyright (C) 2012 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.engine.view;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import com.opengamma.engine.depgraph.DependencyGraph;
import com.opengamma.engine.depgraph.DependencyNode;
import com.opengamma.engine.value.ValueSpecification;
import com.opengamma.engine.view.compilation.CompiledViewDefinitionWithGraphsImpl;
import com.opengamma.util.tuple.Pair;

/**
 * Represents a queryable source of the execution log mode to apply for a value specification.
 */
public class ExecutionLogModeSource {

  private final ReentrantLock _lock = new ReentrantLock();
  private final Map<Pair<String, ValueSpecification>, Integer> _elevatedLogTargets = new HashMap<Pair<String, ValueSpecification>, Integer>();
  private final Map<DependencyNode, Integer> _elevatedLogNodes = new ConcurrentHashMap<DependencyNode, Integer>();
  private CompiledViewDefinitionWithGraphsImpl _compiledViewDefinition;
  
  /**
   * Ensures at least a minimum level of logging output is present in the results for the given value specifications.
   * Changes will take effect from the next computation cycle.
   * <p>
   * Each call to elevate the minimum level of logging output for a result must be paired with exactly one call to
   * reduce the level of logging output, if required.
   * 
   * @param minimumLogMode  the minimum log mode to ensure, not null
   * @param targets  the targets affected, not null or empty
   */
  public void setMinimumLogMode(ExecutionLogMode minimumLogMode, Set<Pair<String, ValueSpecification>> targets) {
    // Synchronization ensures only one writer, while getLogMode is allowed to read from the ConcurrentHashMap
    // without further locking.
    switch (minimumLogMode) {
      case INDICATORS:
        _lock.lock();
        try {
          for (Pair<String, ValueSpecification> target : targets) {
            if (decrementRefCount(target, _elevatedLogTargets)) {
              removeElevatedNode(target);
            }
          }
        } finally {
          _lock.unlock();
        }
        break;
      case FULL:
        _lock.lock();
        try {
          for (Pair<String, ValueSpecification> target : targets) {
            if (incrementRefCount(target, _elevatedLogTargets)) {
              addElevatedNode(target);
            }
          }
        } finally {
          _lock.unlock();
        }
        break;
    }
  }
  
  /**
   * Gets the log mode for a dependency node.
   *
   * @param dependencyNode  the dependency node, not null
   * @return the log mode, not null
   */
  public ExecutionLogMode getLogMode(DependencyNode dependencyNode) {
    return _elevatedLogNodes.containsKey(dependencyNode) ? ExecutionLogMode.FULL : ExecutionLogMode.INDICATORS;
  }
  
  //-------------------------------------------------------------------------
  /*package*/ void viewDefinitionCompiled(CompiledViewDefinitionWithGraphsImpl compiledViewDefinition) {
    _lock.lock();
    try {
      _compiledViewDefinition = compiledViewDefinition;
      rebuildNodeLogModes();
    } finally {
      _lock.unlock();
    }
  }
  
  private void addElevatedNode(Pair<String, ValueSpecification> target) {
    // Must be called while holding the lock
    incrementNodeRefCount(getNodeProducing(target));
  }
  
  private void incrementNodeRefCount(DependencyNode node) {
    if (node == null) {
      return;
    }
    incrementRefCount(node, _elevatedLogNodes);
    for (DependencyNode inputNode : node.getInputNodes()) {
      incrementNodeRefCount(inputNode);
    }
  }
  
  private void removeElevatedNode(Pair<String, ValueSpecification> target) {
    // Must be called while holding the lock
    decrementNodeRefCount(getNodeProducing(target));
  }
  
  private void decrementNodeRefCount(DependencyNode node) {
    if (node == null) {
      return;
    }
    decrementRefCount(node, _elevatedLogNodes);
    for (DependencyNode inputNode : node.getInputNodes()) {
      decrementNodeRefCount(inputNode);
    }
  }
  
  private DependencyNode getNodeProducing(Pair<String, ValueSpecification> target) {
    if (_compiledViewDefinition == null) {
      return null;
    }
    String calcConfigName = target.getFirst();
    ValueSpecification valueSpec = target.getSecond();
    DependencyGraph depGraph = _compiledViewDefinition.getDependencyGraph(calcConfigName);
    if (depGraph == null) {
      return null;
    }
    DependencyNode node = depGraph.getNodeProducing(valueSpec);
    return node;
  }
  
  private void rebuildNodeLogModes() {
    // Must be called while holding the lock
    _elevatedLogNodes.clear();
    for (Pair<String, ValueSpecification> target : _elevatedLogTargets.keySet()) {
      addElevatedNode(target);
    }
  }
  
  //-------------------------------------------------------------------------
  private <T> boolean incrementRefCount(T key, Map<T, Integer> refMap) {
    Integer refCount = refMap.get(key);
    if (refCount == null) {
      refMap.put(key, 1);
      return true;
    } else {
      refMap.put(key, refCount + 1);
      return false;
    }
  }
  
  private <T> boolean decrementRefCount(T key, Map<T, Integer> refMap) {
    Integer value = refMap.get(key);
    if (value == null) {
      return false;
    }
    if (value == 1) {
      refMap.remove(key);
      return true;
    } else {
      refMap.put(key, value - 1);
      return false;
    }
  }
  
}
