/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.engine.view.calcnode.stats;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.threeten.bp.Instant;

import com.opengamma.util.tuple.ObjectsPair;
import com.opengamma.util.tuple.Pair;

/**
 * In-memory storage of function costs.
 * <p>
 * This implementation does not support history.
 */
public class InMemoryFunctionCostsMaster implements FunctionCostsMaster {

  /**
   * The store of documents.
   */
  private Map<Pair<String, String>, FunctionCostsDocument> _data = new ConcurrentHashMap<Pair<String, String>, FunctionCostsDocument>();

  /**
   * Creates an instance.
   */
  public InMemoryFunctionCostsMaster() {
  }

  //-------------------------------------------------------------------------
  @Override
  public FunctionCostsDocument load(String configuration, String functionId, Instant versionAsOf) {
    Pair<String, String> pair = new ObjectsPair<String, String>(configuration, functionId);
    FunctionCostsDocument doc = _data.get(pair);
    return doc != null ? doc.clone() : null;
  }

  @Override
  public FunctionCostsDocument store(final FunctionCostsDocument costs) {
    Pair<String, String> pair = new ObjectsPair<String, String>(costs.getConfigurationName(), costs.getFunctionId());
    costs.setVersion(Instant.now());
    _data.put(pair, costs.clone());
    return costs;
  }

  /**
   * Gets the number of stored documents, used in testing.
   * 
   * @return the size of the master
   */
  public int size() {
    return _data.size();
  }

}
