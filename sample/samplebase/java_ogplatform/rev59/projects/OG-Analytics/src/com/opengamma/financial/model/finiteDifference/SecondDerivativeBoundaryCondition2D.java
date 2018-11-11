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
* boundary condition, i.e. d^2u/dx^2(A,t) = f(t), where A is the boundary level, and f(t) is some specified function of time
*/
public class SecondDerivativeBoundaryCondition2D implements BoundaryCondition2D {

  private final Surface<Double, Double, Double> _f;
  private final double _level;

  public SecondDerivativeBoundaryCondition2D(final Surface<Double, Double, Double> boundarySecondDeriviative, double boundaryLevel) {
    Validate.notNull(boundarySecondDeriviative, "boundaryValue ");
    _f = boundarySecondDeriviative;
    _level = boundaryLevel;
  }

  public SecondDerivativeBoundaryCondition2D(final double boundarySecondDeriviative, double boundaryLevel) {
    _f = ConstantDoublesSurface.from(boundarySecondDeriviative);
    _level = boundaryLevel;
  }

  @Override
  public double getConstant(PDEDataBundle data, double t, double boundaryPosition, double gridSpacing) {
    return _f.getZValue(t, boundaryPosition) * gridSpacing * gridSpacing;
  }

  @Override
  public double[] getLeftMatrixCondition(PDEDataBundle data, double t, double boundaryPosition) {
    return new double[] {1, -2, 1};
  }

  @Override
  public double getLevel() {
    return _level;
  }

  @Override
  public double[] getRightMatrixCondition(PDEDataBundle data, double t, double boundaryPosition) {
    return new double[0];
  }

}
