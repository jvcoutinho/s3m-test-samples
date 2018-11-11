/**
 * Copyright (C) 2009 - 2010 by OpenGamma Inc.
 *
 * Please see distribution for license.
 */
package com.opengamma.engine.view.calcnode;


/**
 * An interface for any system through which {@link CalculationJob}s can be sent and
 * {@link CalculationJobResult}s received.
 *
 * @author kirk
 */
public interface JobRequestSender {

  void sendRequest(CalculationJob job,
      JobResultReceiver resultReceiver);
}
