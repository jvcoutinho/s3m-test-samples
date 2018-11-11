/**
 * Copyright (C) 2012 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.financial.analytics.volatility.surface;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opengamma.OpenGammaRuntimeException;
import com.opengamma.engine.ComputationTarget;
import com.opengamma.financial.analytics.model.InstrumentTypeProperties;
import com.opengamma.util.money.Currency;

/**
 * Constructs ATM volatility surface data objects for swaptions if the target is the currency of the swaption.
 */
public class RawSwaptionATMVolatilitySurfaceDataFunction extends RawVolatilitySurfaceDataFunction {
  /** The logger */
  private static final Logger s_logger = LoggerFactory.getLogger(RawSwaptionATMVolatilitySurfaceDataFunction.class);

  /**
   * Default constructor
   */
  public RawSwaptionATMVolatilitySurfaceDataFunction() {
    super(InstrumentTypeProperties.SWAPTION_ATM);
  }

  @Override
  public boolean isCorrectIdType(final ComputationTarget target) {
    if (target.getUniqueId() == null) {
      s_logger.error("Target unique id was null; {}", target);
      return false;
    }
    return Currency.OBJECT_SCHEME.equals(target.getUniqueId().getScheme());
  }

  @Override
  protected VolatilitySurfaceDefinition<?, ?> getDefinition(final VolatilitySurfaceDefinitionSource definitionSource, final ComputationTarget target, final String definitionName) {
    final String fullDefinitionName = definitionName + "_" + target.getUniqueId().getValue();
    final VolatilitySurfaceDefinition<?, ?> definition = definitionSource.getDefinition(fullDefinitionName, InstrumentTypeProperties.SWAPTION_ATM);
    if (definition == null) {
      throw new OpenGammaRuntimeException("Could not get volatility surface definition named " + fullDefinitionName + " for instrument type " + InstrumentTypeProperties.SWAPTION_ATM);
    }
    return definition;
  }

  @Override
  protected VolatilitySurfaceSpecification getSpecification(final VolatilitySurfaceSpecificationSource specificationSource, final ComputationTarget target, final String specificationName) {
    final String fullSpecificationName = specificationName + "_" + target.getUniqueId().getValue();
    final VolatilitySurfaceSpecification specification = specificationSource.getSpecification(fullSpecificationName, InstrumentTypeProperties.SWAPTION_ATM);
    if (specification == null) {
      throw new OpenGammaRuntimeException("Could not get volatility surface specification named " + fullSpecificationName + " for instrument type " + InstrumentTypeProperties.SWAPTION_ATM);
    }
    return specification;
  }

}
