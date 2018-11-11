/**
 * Copyright (C) 2009 - 2011 by OpenGamma Inc.
 *
 * Please see distribution for license.
 */
package com.opengamma.engine.management;

import java.util.Set;

import com.opengamma.id.UniqueIdentifier;

/**
 * A management bean for a ViewProcessor
 *
 */
public interface ViewProcessorMBean {
  
  /**
   * Gets a collection of the view processes currently being managed by this view processor. A view process could be in
   * any state, and might have finished producing new results. 
   * 
   * @return a collection of the current view processes, not null
   */
  Set<UniqueIdentifier> getViewProcesses();
  
  /**
   * Start this view processor
   */
  void start();

  /**
   * Stop this view processor
   * 
   */
  void stop();

  /**
   * Check whether this view processor is currently running.
   * @return whether the component is currently running
   */
  boolean isRunning();
  
}
