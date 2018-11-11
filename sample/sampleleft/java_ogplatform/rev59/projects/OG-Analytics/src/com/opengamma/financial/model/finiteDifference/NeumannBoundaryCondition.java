/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.financial.model.finiteDifference;

import org.apache.commons.lang.Validate;

import com.opengamma.math.function.Function1D;

/**
 * Neumann boundary condition, i.e. du/dx(A,t) = f(t), where A is the boundary level, and f(t) is some specified function of time
 */
public class NeumannBoundaryCondition implements BoundaryCondition {

  private final Function1D<Double, Double> _timeValue;
  private final double _level;

  /**
   * Neumann  boundary condition, i.e. du/dx(A,t) = f(t), where A is the boundary level, and f(t) is some specified function of time
   * @param timeValue The value of u at the boundary, i.e. du/dx(A,t) = f(t) 
   * @param level The boundary level (A)
   */
  public NeumannBoundaryCondition(final Function1D<Double, Double> timeValue, double level) {
    Validate.notNull(timeValue, "null timeValue");
    _timeValue = timeValue;
    _level = level;
  }

  public NeumannBoundaryCondition(final double fixedValue, final double level) {
    _timeValue = new Function1D<Double, Double>() {

      @Override
      public Double evaluate(Double x) {
        return fixedValue;
      }
    };
    _level = level;
  }

  @Override
  public double getConstant(final PDEDataBundle data, final double t, final double dx) {
    return _timeValue.evaluate(t) * dx;
  }

  @Override
  public double[] getLeftMatrixCondition(PDEDataBundle data, double t) {
    return new double[] {-1, 1};
  }

  @Override
  public double getLevel() {
    return _level;
  }

  @Override
  public double[] getRightMatrixCondition(PDEDataBundle data, double t) {
    return new double[0];
  }

}
