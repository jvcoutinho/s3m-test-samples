/**
 * Copyright (C) 2012 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.financial.model.volatility.surface;

import org.apache.commons.lang.Validate;

import com.opengamma.financial.model.interestrate.curve.ForwardCurve;
import com.opengamma.math.surface.Surface;

/**
 *  * A surface that contains the Black (implied) volatility  as a function of time to maturity and moneyness, m, defined
 *  as m = k/F(T), where k is the strike and F(T) is the forward for expiry at time T
 */
public class BlackVolatilitySurfaceMoneyness extends BlackVolatilitySurface<Moneyness> {

  private ForwardCurve _fc;

  /**
   * @param surface A implied volatility surface parameterised by time and moneyness m = strike/forward
   * @param forwardCurve the forward curve
   */
  public BlackVolatilitySurfaceMoneyness(final Surface<Double, Double, Double> surface, final ForwardCurve forwardCurve) {
    super(surface);
    Validate.notNull(forwardCurve, "null forward curve");
    _fc = forwardCurve;
  }

  /**
   * Return a volatility for the expiry, strike pair provided.
   * Interpolation/extrapolation behaviour depends on underlying surface
   * @param t time to maturity
   * @param k strike
   * @return The Black (implied) volatility
   */
  @Override
  public double getVolatility(final double t, final double k) {
    final double f = _fc.getForward(t);
    final Moneyness x = new Moneyness(k, f);
    return getVolatility(t, x);
  }

  /**
   * Return a volatility for the expiry, moneyness pair provided.
   * Interpolation/extrapolation behaviour depends on underlying surface
   * @param t time to maturity
   * @param m the moneyness  m = k/F(T), where k is the strike and F(T) is the forward for expiry at time T
   * @return The Black (implied) volatility
   */
  public double getVolatilityForMoneyness(final double t, final double m) {
    return getVolatility(t, new Moneyness(m));
  }

  public ForwardCurve getForwardCurve() {
    return _fc;
  }

}
