/**
 * Copyright (C) 2011 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.financial.interestrate.curve;

import static org.testng.AssertJUnit.assertEquals;

import org.testng.annotations.Test;

import com.opengamma.financial.model.interestrate.curve.ForwardCurve;
import com.opengamma.financial.model.interestrate.curve.YieldAndDiscountCurve;
import com.opengamma.financial.model.interestrate.curve.YieldCurve;
import com.opengamma.math.curve.ConstantDoublesCurve;
import com.opengamma.math.curve.FunctionalDoublesCurve;
import com.opengamma.math.curve.InterpolatedDoublesCurve;
import com.opengamma.math.function.Function;
import com.opengamma.math.function.Function1D;
import com.opengamma.math.interpolation.CombinedInterpolatorExtrapolator;
import com.opengamma.math.interpolation.DoubleQuadraticInterpolator1D;
import com.opengamma.math.interpolation.FlatExtrapolator1D;

/**
 * 
 */
public class ForwardCurveTest {
  private static final DoubleQuadraticInterpolator1D INTERPOLATOR_1D = new DoubleQuadraticInterpolator1D();
  private static final CombinedInterpolatorExtrapolator EXTRAPOLATOR_1D = new CombinedInterpolatorExtrapolator(INTERPOLATOR_1D, new FlatExtrapolator1D());
  private static double DRIFT = 0.05;
  private static final double[] EXPIRIES = new double[] {0.0, 7. / 365, 14 / 365., 21 / 365., 1 / 12., 3 / 12., 0.5, 0.75, 1, 5, 10 };
  private static final double SPOT = 1.34;
  private static final double[] FORWARDS;
  private static final ForwardCurve FORWARD_CURVE;

  static {
    int n = EXPIRIES.length;
    FORWARDS = new double[n];
    for (int i = 0; i < n; i++) {
      FORWARDS[i] = SPOT * Math.exp(DRIFT * EXPIRIES[i]);
    }
    FORWARD_CURVE = new ForwardCurve(InterpolatedDoublesCurve.from(EXPIRIES, FORWARDS, EXTRAPOLATOR_1D));
  }

  @Test
  public void testFlat() {
    ForwardCurve fc = new ForwardCurve(SPOT);
    assertEquals(SPOT, fc.getForward(4.56), 1e-9);
    assertEquals(0.0, fc.getDrift(1.4), 1e-9);
  }

  @Test
  public void testConstDrift() {
    ForwardCurve fc = new ForwardCurve(SPOT, DRIFT);
    final double t = 5.67;
    assertEquals(SPOT * Math.exp(t * DRIFT), fc.getForward(t), 1e-9);
    assertEquals(DRIFT, fc.getDrift(1.4), 1e-9);
  }

  @Test
  public void testFunctional() {
    Function<Double, Double> f = new Function<Double, Double>() {

      @Override
      public Double evaluate(Double... x) {
        final double t = x[0];
        return SPOT * (1 + 0.1 * t);
      }
    };
    ForwardCurve fc = new ForwardCurve(FunctionalDoublesCurve.from(f));
    final double t = 5.67;
    assertEquals(f.evaluate(t), fc.getForward(t), 1e-9);
    assertEquals(0.1 / (1 + 0.1 * t), fc.getDrift(t), 1e-9);
  }

  @Test
  public void testDriftCurve() {
    ForwardCurve fc = new ForwardCurve(SPOT,ConstantDoublesCurve.from(DRIFT));
    final double t = 5.67;
    assertEquals(SPOT*Math.exp(t*DRIFT), fc.getForward(t), 1e-9);
    assertEquals(DRIFT, fc.getDrift(t), 1e-9);
  }

  @Test
  public void testTwoCurves() {
    final double rate = 0.05;
    final double cc = 0.02;
    YieldAndDiscountCurve r = new YieldCurve(ConstantDoublesCurve.from(rate));
    YieldAndDiscountCurve q = new YieldCurve(ConstantDoublesCurve.from(cc));
    ForwardCurve fc = new ForwardCurve(SPOT,r,q);
    final double t = 5.67;
    assertEquals(SPOT*Math.exp(t*(rate-cc)), fc.getForward(t), 1e-9);
    assertEquals(rate-cc, fc.getDrift(t), 1e-9);
  }

  @Test
  public void testShift() {
    final double shift = 0.1;

    ForwardCurve shifedCurve = FORWARD_CURVE.withFractionalShift(shift);
    assertEquals(SPOT*(1 + shift), shifedCurve.getSpot(), 0.0);

    for (int i = 0; i < EXPIRIES.length - 1; i++) {
      double t = EXPIRIES[i];
      assertEquals("time " + i, SPOT*(1 + shift)* Math.exp(DRIFT * t), shifedCurve.getForward(t), 1e-9);
    }

    Function1D<Double, Double> func = shifedCurve.getForwardCurve().toFunction1D();
    for (int i = 0; i < EXPIRIES.length - 1; i++) {
      double t = EXPIRIES[i];
      assertEquals("time " + i, SPOT*(1 + shift) * Math.exp(DRIFT * t), func.evaluate(t), 1e-9);
    }
  }
}
