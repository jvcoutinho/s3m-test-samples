/**
 * Copyright (C) 2009 - 2011 by OpenGamma Inc.
 * 
 * Please see distribution for license.
 */
package com.opengamma.financial.model.finiteDifference;

import org.apache.commons.lang.Validate;

import com.opengamma.math.surface.ConstantDoublesSurface;
import com.opengamma.math.surface.Surface;

/**
 * Dirichlet boundary condition, i.e. u(A, x, t) = f(x, t), where A is the boundary level of one of the spatial dimensions, and f(x, t) is some specified 
 * function of time and the other spatial dimension 
 */
public class DirichletBoundaryCondition2D implements BoundaryCondition2D {

  private final Surface<Double, Double, Double> _f;
  private final double _level;

  /**
   * Dirichlet boundary condition, i.e. u(A, x, t) = f(x, t), where A is the boundary level of one of the spatial dimensions, and f(x, t) is some specified function of time 
   * and the other spatial dimension 
   * @param boundaryValue The value of u at the boundary, i.e. u(A, x, t) = f(x, t) 
   * @param boundaryLevel The boundary level (A)
   */
  public DirichletBoundaryCondition2D(final Surface<Double, Double, Double> boundaryValue, double boundaryLevel) {
    Validate.notNull(boundaryValue, "boundaryValue ");
    _f = boundaryValue;
    _level = boundaryLevel;
  }

  public DirichletBoundaryCondition2D(final double fixedValue, final double boundaryLevel) {
    _f = ConstantDoublesSurface.from(fixedValue);
    _level = boundaryLevel;
  }

  @Override
  public double[] getLeftMatrixCondition(PDEDataBundle data, double t, double x) {
    return new double[] {1.0};
  }

  @Override
  public double getLevel() {
    return _level;
  }

  @Override
  public double[] getRightMatrixCondition(PDEDataBundle data, double t, double x) {
    return new double[0];
  }

  @Override
  public double getConstant(PDEDataBundle data, double t, double boundaryPosition, double gridSpacing) {
    return _f.getZValue(t, boundaryPosition);
  }

}
