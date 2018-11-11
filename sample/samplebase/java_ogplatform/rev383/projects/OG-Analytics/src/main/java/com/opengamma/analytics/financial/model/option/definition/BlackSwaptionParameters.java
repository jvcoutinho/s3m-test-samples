/**
 * Copyright (C) 2012 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.analytics.financial.model.option.definition;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.Validate;

import com.opengamma.analytics.financial.instrument.index.GeneratorSwapFixedIbor;
import com.opengamma.analytics.financial.model.volatility.VolatilityModel;
import com.opengamma.analytics.math.surface.Surface;
import com.opengamma.util.tuple.DoublesPair;

/**
 * Class describing the Black volatility surface used in swaption modeling.
 */
public class BlackSwaptionParameters implements VolatilityModel<double[]> {

  /**
   * The volatility surface. The first dimension is the expiration and the second the tenor.
   */
  private final Surface<Double, Double, Double> _volatility;
  /**
   * The standard swap generator (in particular fixed leg convention and floating leg tenor) for which the volatility surface is valid.
   */
  private final GeneratorSwapFixedIbor _generatorSwap;

  /**
   * Constructor from the parameter surfaces. The default SABR volatility formula is HaganVolatilityFunction.
   * @param volatility The Black volatility surface.
   * @param generatorSwap The standard swap generator for which the volatility surface is valid.
   */
  public BlackSwaptionParameters(final Surface<Double, Double, Double> volatility, final GeneratorSwapFixedIbor generatorSwap) {
    Validate.notNull(volatility, "volatility surface");
    Validate.notNull(generatorSwap, "Swap generator");
    _volatility = volatility;
    _generatorSwap = generatorSwap;
  }

  /**
   * Return the volatility for a pair of time to expiration and instrument tenor.
   * @param expiryMaturity The expiration/tenor pair.
   * @return The volatility.
   */
  public double getVolatility(final DoublesPair expiryMaturity) {
    return _volatility.getZValue(expiryMaturity);
  }

  /**
   * Return the volatility for a pair of time to expiration and instrument tenor.
   * @param expiry The time to expiration
   * @param maturity The expiration/tenor pair.
   * @return The volatility.
   */
  public double getVolatility(final double expiry, final double maturity) {
    return _volatility.getZValue(expiry, maturity);
  }

  @Override
  /**
   * Return the volatility for a expiration/instrument tenor array.
   * @param data An array of two doubles with [0] the expiration, [1] instrument tenor.
   * @return The volatility.
   */
  public Double getVolatility(final double[] data) {
    Validate.notNull(data, "data");
    Validate.isTrue(data.length == 2, "data should have two components (expiration, instrument tenor)");
    return getVolatility(data[0], data[1]);
  }

  /**
   * Gets the standard swap generator for which the volatility surface is valid.
   * @return The swap generator.
   */
  public GeneratorSwapFixedIbor getGeneratorSwap() {
    return _generatorSwap;
  }

  /**
   * Gets the volatility surface.
   * @return The volatility surface.
   */
  public Surface<Double, Double, Double> getVolatilitySurface() {
    return _volatility;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + _generatorSwap.hashCode();
    result = prime * result + _volatility.hashCode();
    return result;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final BlackSwaptionParameters other = (BlackSwaptionParameters) obj;
    if (!ObjectUtils.equals(_generatorSwap, other._generatorSwap)) {
      return false;
    }
    if (!ObjectUtils.equals(_volatility, other._volatility)) {
      return false;
    }
    return true;
  }

}
