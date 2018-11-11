/**
 * Copyright (C) 2009 - 2011 by OpenGamma Inc.
 * 
 * Please see distribution for license.
 */
package com.opengamma.financial.model.finiteDifference;

import org.apache.commons.lang.Validate;

import com.opengamma.math.cube.Cube;
import com.opengamma.math.linearalgebra.Decomposition;
import com.opengamma.math.linearalgebra.LUDecompositionCommons;

/**
 * Peaceman-Rachford splitting
 * <b>Note</b> this is for testing purposes and is not recommended for actual use 
 */
public class PeacemanRachfordFiniteDifference2D implements ConvectionDiffusionPDESolver2D {

  private static final Decomposition<?> DCOMP = new LUDecompositionCommons();
  // Theta = 0 - explicit
  private static final double THETA = 0.5;

  @Override
  public double[][] solve(ConvectionDiffusion2DPDEDataBundle pdeData, int tSteps, int xSteps, int ySteps, double tMax, BoundaryCondition2D xLowerBoundary, BoundaryCondition2D xUpperBoundary,
      BoundaryCondition2D yLowerBoundary, BoundaryCondition2D yUpperBoundary) {
    return solve(pdeData, tSteps, xSteps, ySteps, tMax, xLowerBoundary, xUpperBoundary, yLowerBoundary, yUpperBoundary, null);
  }

  public double[][] solve(ConvectionDiffusion2DPDEDataBundle pdeData, final int tSteps, final int xSteps, final int ySteps, final double tMax, BoundaryCondition2D xLowerBoundary,
      BoundaryCondition2D xUpperBoundary, BoundaryCondition2D yLowerBoundary, BoundaryCondition2D yUpperBoundary, final Cube<Double, Double, Double, Double> freeBoundary) {

    double dt = tMax / (tSteps);
    double dx = (xUpperBoundary.getLevel() - xLowerBoundary.getLevel()) / (xSteps);
    double dy = (yUpperBoundary.getLevel() - yLowerBoundary.getLevel()) / (ySteps);
    double dtdx2 = dt / dx / dx;
    double dtdx = dt / dx;
    double dtdy2 = dt / dy / dy;
    double dtdy = dt / dy;

    double[][] v = new double[xSteps + 1][ySteps + 1];

    double[][] vRight = new double[xSteps + 1][ySteps + 1];
    double[] x = new double[xSteps + 1];
    double[] y = new double[ySteps + 1];

    final double[] q = new double[xSteps + 1];
    final double[] r = new double[ySteps + 1];
    final double[][] mx = new double[xSteps + 1][xSteps + 1];
    final double[][] my = new double[ySteps + 1][ySteps + 1];

    double currentX = 0;
    double currentY = 0;

    for (int j = 0; j <= ySteps; j++) {
      currentY = yLowerBoundary.getLevel() + j * dy;
      y[j] = currentY;
    }
    for (int i = 0; i <= xSteps; i++) {
      currentX = xLowerBoundary.getLevel() + i * dx;
      x[i] = currentX;
      for (int j = 0; j <= ySteps; j++) {
        v[i][j] = pdeData.getInitialValue(x[i], y[j]);
      }
    }

    double t = 0.0;
    double a, b, c, d, f;

    for (int n = 0; n < tSteps; n++) {
      // t += dt / 2;

      // stag 1 Explicit in y, implicit in x
      for (int i = 1; i < xSteps; i++) {
        for (int j = 1; j < ySteps; j++) {
          c = pdeData.getC(t, x[i], y[j]);
          d = pdeData.getD(t, x[i], y[j]);
          f = pdeData.getF(t, x[i], y[j]);

          vRight[i][j] = (1 - 0.25 * dt * c) * v[i][j];
          vRight[i][j] -= 0.5 * dtdy2 * d * (v[i][j + 1] + v[i][j - 1] - 2 * v[i][j]);
          vRight[i][j] -= 0.25 * dtdy * f * (v[i][j + 1] - v[i][j - 1]);
        }
      }

      t += dt / 2;

      for (int j = 1; j < ySteps; j++) {
        for (int i = 1; i < xSteps; i++) {
          a = pdeData.getA(t, x[i], y[j]);
          b = pdeData.getB(t, x[i], y[j]);
          c = pdeData.getC(t, x[i], y[j]);

          mx[i][i - 1] = 0.5 * (dtdx2 * a - 0.5 * dtdx * b);
          mx[i][i] = 1 + 0.5 * (-2 * dtdx2 * a + 0.5 * dt * c);
          mx[i][i + 1] = 0.5 * (dtdx2 * a + 0.5 * dtdx * b);

          q[i] = vRight[i][j];
        }

        double[] temp = xLowerBoundary.getLeftMatrixCondition(pdeData, t, y[j]);
        for (int k = 0; k < temp.length; k++) {
          mx[0][k] = temp[k];
        }
        temp = xUpperBoundary.getLeftMatrixCondition(pdeData, t, y[j]);
        for (int k = 0; k < temp.length; k++) {
          mx[xSteps][xSteps - k] = temp[k];
        }

        temp = xLowerBoundary.getRightMatrixCondition(pdeData, t, y[j]);
        double sum = 0;
        for (int k = 0; k < temp.length; k++) {
          sum += temp[k] * v[k][j];
        }
        q[0] = sum + xLowerBoundary.getConstant(pdeData, t, y[j], dx);

        temp = xUpperBoundary.getRightMatrixCondition(pdeData, t, y[j]);
        sum = 0;
        for (int k = 0; k < temp.length; k++) {
          sum += temp[k] * v[xSteps - k][j];
        }
        q[xSteps] = sum + xUpperBoundary.getConstant(pdeData, t, y[j], dx);

        // SOR
        final double omega = 1.5;
        double scale = 1.0;
        double errorSqr = Double.POSITIVE_INFINITY;
        int min, max;
        int count = 0;
        while (errorSqr / (scale + 1e-10) > 1e-18 && count < 1000) {
          errorSqr = 0.0;
          scale = 0.0;
          for (int l = 0; l <= xSteps; l++) {
            min = (l == xSteps ? 0 : Math.max(0, l - 1));
            max = (l == 0 ? xSteps : Math.min(xSteps, l + 1));
            sum = 0;
            // for (int k = 0; k <= xSteps; k++) {
            for (int k = min; k <= max; k++) {// mx is tri-diagonal so only need 3 steps here
              sum += mx[l][k] * v[k][j];
            }
            double correction = omega / mx[l][l] * (q[l] - sum);
            // if (freeBoundary != null) {
            // correction = Math.max(correction, freeBoundary.getZValue(t, x[j]) - f[j]);
            // }
            errorSqr += correction * correction;
            v[l][j] += correction;
            scale += v[l][j] * v[l][j];
          }
          count++;
        }
        Validate.isTrue(count < 1000, "SOR exceeded max interations");
      }

      // get the y = 0 and y = yStep boundaries
      for (int i = 0; i <= xSteps; i++) {

        double[] temp = yLowerBoundary.getRightMatrixCondition(pdeData, t, x[i]);
        double sum = 0;
        for (int k = 0; k < temp.length; k++) {
          sum += temp[k] * v[i][k];// TODO this should be vold
        }
        sum += yLowerBoundary.getConstant(pdeData, t, x[i], dy);

        temp = yLowerBoundary.getLeftMatrixCondition(pdeData, t, x[i]);
        for (int k = 1; k < temp.length; k++) {
          sum -= temp[k] * v[i][k];
        }
        v[i][0] = sum / temp[0];

        temp = yUpperBoundary.getRightMatrixCondition(pdeData, t, x[i]);
        sum = 0;
        for (int k = 0; k < temp.length; k++) {
          sum += temp[k] * v[i][ySteps - k];
        }
        sum += yUpperBoundary.getConstant(pdeData, t, x[i], dy);

        temp = yUpperBoundary.getLeftMatrixCondition(pdeData, t, x[i]);
        for (int k = 1; k < temp.length; k++) {
          sum -= temp[k] * v[i][ySteps - k];
        }
        v[i][ySteps] = sum / temp[0];
      }

      // // copy the boundary points from the previous level
      // for (int i = 0; i <= xSteps; i++) {
      // vStar[i][0] = v[i][0];
      // vStar[i][ySteps] = v[i][ySteps];
      // }

      // stag 2 explicit in x, implicit in y
      for (int j = 1; j < ySteps; j++) {
        for (int i = 1; i < xSteps; i++) {

          a = pdeData.getA(t, x[i], y[j]);
          b = pdeData.getB(t, x[i], y[j]);
          c = pdeData.getC(t, x[i], y[j]);

          vRight[i][j] = (1 - 0.25 * dt * c) * v[i][j];
          vRight[i][j] -= 0.5 * dtdx2 * a * (v[i + 1][j] + v[i - 1][j] - 2 * v[i][j]);
          vRight[i][j] -= 0.25 * dtdx * b * (v[i + 1][j] - v[i - 1][j]);
        }
      }

      t += dt / 2;

      for (int i = 1; i < xSteps; i++) {
        for (int j = 1; j < ySteps; j++) {

          c = pdeData.getC(t, x[i], y[j]);
          d = pdeData.getD(t, x[i], y[j]);
          f = pdeData.getF(t, x[i], y[j]);

          my[j][j - 1] = 0.5 * (dtdy2 * d - 0.5 * dtdy * f);
          my[j][j] = 1 + 0.5 * (-2 * dtdy2 * d + 0.5 * dt * c);
          my[j][j + 1] = 0.5 * (dtdy2 * d + 0.5 * dtdy * f);

          r[j] = vRight[i][j];
        }

        double[] temp = yLowerBoundary.getLeftMatrixCondition(pdeData, t, x[i]);
        for (int k = 0; k < temp.length; k++) {
          my[0][k] = temp[k];
        }
        temp = yUpperBoundary.getLeftMatrixCondition(pdeData, t, x[i]);
        for (int k = 0; k < temp.length; k++) {
          my[ySteps][ySteps - k] = temp[k];
        }

        temp = yLowerBoundary.getRightMatrixCondition(pdeData, t, x[i]);
        double sum = 0;
        for (int k = 0; k < temp.length; k++) {
          sum += temp[k] * v[i][k];
        }
        r[0] = sum + yLowerBoundary.getConstant(pdeData, t, x[i], dy);

        temp = yUpperBoundary.getRightMatrixCondition(pdeData, t, x[i]);
        sum = 0;
        for (int k = 0; k < temp.length; k++) {
          sum += temp[k] * v[i][ySteps - k];
        }
        r[ySteps] = sum + yUpperBoundary.getConstant(pdeData, t, x[i], dy);

        // SOR
        final double omega = 1.5;
        double scale = 1.0;
        double errorSqr = Double.POSITIVE_INFINITY;
        int count = 0;
        while (errorSqr / (scale + 1e-10) > 1e-18 && count < 1000) {
          errorSqr = 0.0;
          scale = 0.0;
          int min, max;
          for (int l = 0; l <= ySteps; l++) {
            min = (l == ySteps ? 0 : Math.max(0, l - 1));
            max = (l == 0 ? ySteps : Math.min(ySteps, l + 1));
            sum = 0;
            // for (int k = 0; k <= ySteps; k++) {
            for (int k = min; k <= max; k++) {
              sum += my[l][k] * v[i][k];
            }
            double correction = omega / my[l][l] * (r[l] - sum);
            // if (freeBoundary != null) {
            // correction = Math.max(correction, freeBoundary.getZValue(t, x[j]) - f[j]);
            // }
            errorSqr += correction * correction;
            v[i][l] += correction;
            scale += v[i][l] * v[i][l];
          }
          count++;
        }
        Validate.isTrue(count < 1000, "SOR exceeded max interations");
      }

      // still have to handle the i = 0 and i = xSteps boundary
      for (int j = 0; j <= ySteps; j++) {

        double[] temp = xLowerBoundary.getRightMatrixCondition(pdeData, t, y[j]);
        double sum = 0;
        for (int k = 0; k < temp.length; k++) {
          sum += temp[k] * v[k][j];// TODO this should be vold
        }
        sum += xLowerBoundary.getConstant(pdeData, t, y[j], dx);

        temp = xLowerBoundary.getLeftMatrixCondition(pdeData, t, y[j]);
        for (int k = 1; k < temp.length; k++) {
          sum -= temp[k] * v[k][j];
        }
        v[0][j] = sum / temp[0];

        temp = xUpperBoundary.getRightMatrixCondition(pdeData, t, y[j]);
        sum = 0;
        for (int k = 0; k < temp.length; k++) {
          sum += temp[k] * v[xSteps - k][j];
        }
        sum += xUpperBoundary.getConstant(pdeData, t, y[j], dx);

        temp = xUpperBoundary.getLeftMatrixCondition(pdeData, t, y[j]);
        for (int k = 1; k < temp.length; k++) {
          sum -= temp[k] * v[xSteps - k][j];
        }
        v[xSteps][j] = sum / temp[0];
      }

    } // time loop
    return v;

  }

  // private double[][] solveSOR(double[][] m, double[][] v)
}
