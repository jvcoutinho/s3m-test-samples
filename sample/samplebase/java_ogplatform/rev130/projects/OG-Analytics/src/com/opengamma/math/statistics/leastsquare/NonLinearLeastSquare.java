/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.math.statistics.leastsquare;

import java.util.Arrays;

import org.apache.commons.lang.Validate;

import com.opengamma.math.FunctionUtils;
import com.opengamma.math.MathException;
import com.opengamma.math.differentiation.VectorFieldFirstOrderDifferentiator;
import com.opengamma.math.function.Function1D;
import com.opengamma.math.function.ParameterizedFunction;
import com.opengamma.math.linearalgebra.Decomposition;
import com.opengamma.math.linearalgebra.DecompositionFactory;
import com.opengamma.math.linearalgebra.DecompositionResult;
import com.opengamma.math.matrix.DoubleMatrix1D;
import com.opengamma.math.matrix.DoubleMatrix2D;
import com.opengamma.math.matrix.DoubleMatrixUtils;
import com.opengamma.math.matrix.MatrixAlgebra;
import com.opengamma.math.matrix.MatrixAlgebraFactory;

/**
 * 
 */
public class NonLinearLeastSquare {
  private static final int MAX_ATTEMPTS = 10000;
  private final double _eps;
  private final Decomposition<?> _decomposition;
  private final MatrixAlgebra _algebra;

  public NonLinearLeastSquare() {
    this(DecompositionFactory.SV_COMMONS, MatrixAlgebraFactory.OG_ALGEBRA, 1e-8);
  }
  
  public NonLinearLeastSquare(Decomposition<?> decomposition, MatrixAlgebra algebra, double eps) {
    _decomposition = decomposition;
    _algebra = algebra;
    _eps = eps;
  }

  /**
   * Use this when the model is in the ParameterizedFunction form and analytic parameter sensitivity is not available 
   * @param x Set of measurement points 
   * @param y Set of measurement values
   * @param func The model in ParameterizedFunction form (i.e. takes measurement points and a set of parameters and returns a model value)
   * @param startPos Initial value of the parameters 
   * @return A LeastSquareResults object
   */
  public LeastSquareResults solve(final DoubleMatrix1D x, final DoubleMatrix1D y, final ParameterizedFunction<Double, DoubleMatrix1D, Double> func, final DoubleMatrix1D startPos) {
    Validate.notNull(x, "x");
    Validate.notNull(y, "y");
    final int n = x.getNumberOfElements();
    Validate.isTrue(y.getNumberOfElements() == n, "y wrong length");
    final double[] sigmas = new double[n];
    Arrays.fill(sigmas, 1); //emcleod 31-1-2011 arbitrary value for now 
    return solve(x, y, new DoubleMatrix1D(sigmas), func, startPos);
  }

  /**
   * Use this when the model is in the ParameterizedFunction form and analytic parameter sensitivity is not available but a measurement error is.
   * @param x Set of measurement points 
   * @param y Set of measurement values
   * @param sigma y Set of measurement errors 
   * @param func The model in ParameterizedFunction form (i.e. takes measurement points and a set of parameters and returns a model value)
   * @param startPos Initial value of the parameters 
   * @return A LeastSquareResults object
   */
  public LeastSquareResults solve(final DoubleMatrix1D x, final DoubleMatrix1D y, final double sigma, final ParameterizedFunction<Double, DoubleMatrix1D, Double> func, final DoubleMatrix1D startPos) {
    Validate.notNull(x, "x");
    Validate.notNull(y, "y");
    Validate.notNull(sigma, "sigma");
    final int n = x.getNumberOfElements();
    Validate.isTrue(y.getNumberOfElements() == n, "y wrong length");
    final double[] sigmas = new double[n];
    Arrays.fill(sigmas, sigma);
    return solve(x, y, new DoubleMatrix1D(sigmas), func, startPos);

  }

  /**
   * Use this when the model is in the ParameterizedFunction form and analytic parameter sensitivity is not available but an array of measurements errors is.
   * @param x Set of measurement points 
   * @param y Set of measurement values
   * @param sigma Set of measurement errors 
   * @param func The model in ParameterizedFunction form (i.e. takes measurement points and a set of parameters and returns a model value)
   * @param startPos Initial value of the parameters 
   * @return A LeastSquareResults object
   */
  public LeastSquareResults solve(final DoubleMatrix1D x, final DoubleMatrix1D y, final DoubleMatrix1D sigma, final ParameterizedFunction<Double, DoubleMatrix1D, Double> func,
      final DoubleMatrix1D startPos) {
    Validate.notNull(x, "x");
    Validate.notNull(y, "y");
    Validate.notNull(sigma, "sigma");

    final int n = x.getNumberOfElements();
    Validate.isTrue(y.getNumberOfElements() == n, "y wrong length");
    Validate.isTrue(sigma.getNumberOfElements() == n, "sigma wrong length");

    final Function1D<DoubleMatrix1D, DoubleMatrix1D> func1D = new Function1D<DoubleMatrix1D, DoubleMatrix1D>() {

      @Override
      public DoubleMatrix1D evaluate(final DoubleMatrix1D theta) {
        final int m = x.getNumberOfElements();
        final double[] res = new double[m];
        for (int i = 0; i < m; i++) {
          res[i] = func.evaluate(x.getEntry(i), theta);
        }
        return new DoubleMatrix1D(res);
      }
    };

    return solve(y, sigma, func1D, startPos);
  }

  /**
   *  Use this when the model is in the ParameterizedFunction form and analytic parameter sensitivity
   * @param x Set of measurement points 
   * @param y Set of measurement values
   * @param func The model in ParameterizedFunction form (i.e. takes a measurement points and a set of parameters and returns a model value)
   * @param grad The model parameter sensitivities in  ParameterizedFunction form (i.e. takes a measurement points and a set of parameters and returns a model parameter sensitivities)
   * @param startPos Initial value of the parameters 
   * @return Initial value of the parameters 
   */
  public LeastSquareResults solve(final DoubleMatrix1D x, final DoubleMatrix1D y, final ParameterizedFunction<Double, DoubleMatrix1D, Double> func,
      final ParameterizedFunction<Double, DoubleMatrix1D, DoubleMatrix1D> grad, final DoubleMatrix1D startPos) {
    Validate.notNull(x, "x");
    Validate.notNull(y, "y");
    Validate.notNull(x, "sigma");
    final int n = x.getNumberOfElements();
    Validate.isTrue(y.getNumberOfElements() == n, "y wrong length");
    final double[] sigmas = new double[n];
    Arrays.fill(sigmas, 1); //emcleod 31-1-2011 arbitrary value for now 
    return solve(x, y, new DoubleMatrix1D(sigmas), func, grad, startPos);
  }

  /**
  *  Use this when the model is in the ParameterizedFunction form and analytic parameter sensitivity and a single measurement error are available 
  * @param x Set of measurement points 
  * @param y Set of measurement values
  * @param sigma Measurement errors 
  * @param func The model in ParameterizedFunction form (i.e. takes a measurement points and a set of parameters and returns a model value)
  * @param grad The model parameter sensitivities in  ParameterizedFunction form (i.e. takes a measurement points and a set of parameters and returns a model parameter sensitivities)
  * @param startPos Initial value of the parameters 
  * @return Initial value of the parameters 
  */
  public LeastSquareResults solve(final DoubleMatrix1D x, final DoubleMatrix1D y, final double sigma, final ParameterizedFunction<Double, DoubleMatrix1D, Double> func,
      final ParameterizedFunction<Double, DoubleMatrix1D, DoubleMatrix1D> grad, final DoubleMatrix1D startPos) {
    Validate.notNull(x, "x");
    Validate.notNull(y, "y");
    final int n = x.getNumberOfElements();
    Validate.isTrue(y.getNumberOfElements() == n, "y wrong length");
    final double[] sigmas = new double[n];
    Arrays.fill(sigmas, sigma);
    return solve(x, y, new DoubleMatrix1D(sigmas), func, grad, startPos);
  }

  /**
   *  Use this when the model is in the ParameterizedFunction form and analytic parameter sensitivity and measurement errors are available 
   * @param x Set of measurement points 
   * @param y Set of measurement values
   * @param sigma Set of measurement errors 
   * @param func The model in ParameterizedFunction form (i.e. takes a measurement points and a set of parameters and returns a model value)
   * @param grad The model parameter sensitivities in  ParameterizedFunction form (i.e. takes a measurement points and a set of parameters and returns a model parameter sensitivities)
   * @param startPos Initial value of the parameters 
   * @return Initial value of the parameters 
   */
  public LeastSquareResults solve(final DoubleMatrix1D x, final DoubleMatrix1D y, final DoubleMatrix1D sigma, final ParameterizedFunction<Double, DoubleMatrix1D, Double> func,
      final ParameterizedFunction<Double, DoubleMatrix1D, DoubleMatrix1D> grad, final DoubleMatrix1D startPos) {
    Validate.notNull(x, "x");
    Validate.notNull(y, "y");
    Validate.notNull(x, "sigma");

    final int n = x.getNumberOfElements();
    Validate.isTrue(y.getNumberOfElements() == n, "y wrong length");
    Validate.isTrue(sigma.getNumberOfElements() == n, "sigma wrong length");

    final Function1D<DoubleMatrix1D, DoubleMatrix1D> func1D = new Function1D<DoubleMatrix1D, DoubleMatrix1D>() {

      @Override
      public DoubleMatrix1D evaluate(final DoubleMatrix1D theta) {
        final int m = x.getNumberOfElements();
        final double[] res = new double[m];
        for (int i = 0; i < m; i++) {
          res[i] = func.evaluate(x.getEntry(i), theta);
        }
        return new DoubleMatrix1D(res);
      }
    };

    final Function1D<DoubleMatrix1D, DoubleMatrix2D> jac = new Function1D<DoubleMatrix1D, DoubleMatrix2D>() {

      @Override
      public DoubleMatrix2D evaluate(final DoubleMatrix1D theta) {
        final int m = x.getNumberOfElements();
        final double[][] res = new double[m][];
        for (int i = 0; i < m; i++) {
          final DoubleMatrix1D temp = grad.evaluate(x.getEntry(i), theta);
          res[i] = temp.getData();
        }
        return new DoubleMatrix2D(res);
      }
    };

    return solve(y, sigma, func1D, jac, startPos);
  }

  /**
   *  Use this when the model is given as a function of its parameters only (i.e. a function that takes a set of parameters and return a set of model values,
   *  so the measurement points are already known to the function), and  analytic parameter sensitivity is not available 
   * @param observedValues Set of measurement values
   * @param func The model as a function of its parameters only
   * @param startPos  Initial value of the parameters 
   * @return Initial value of the parameters 
   */
  public LeastSquareResults solve(final DoubleMatrix1D observedValues, final Function1D<DoubleMatrix1D, DoubleMatrix1D> func, final DoubleMatrix1D startPos) {
    final int n = observedValues.getNumberOfElements();
    final double[] sigma = new double[n];
    Arrays.fill(sigma, 1); //emcleod 31-1-2011 arbitrary value for now 
    final VectorFieldFirstOrderDifferentiator jac = new VectorFieldFirstOrderDifferentiator();
    return solve(observedValues, new DoubleMatrix1D(sigma), func, jac.differentiate(func), startPos);
  }

  /**
   *  Use this when the model is given as a function of its parameters only (i.e. a function that takes a set of parameters and return a set of model values,
   *  so the measurement points are already known to the function), and  analytic parameter sensitivity is not available 
   * @param observedValues Set of measurement values
   * @param sigma Set of measurement errors
   * @param func The model as a function of its parameters only
   * @param startPos  Initial value of the parameters 
   * @return Initial value of the parameters 
   */
  public LeastSquareResults solve(final DoubleMatrix1D observedValues, final DoubleMatrix1D sigma, final Function1D<DoubleMatrix1D, DoubleMatrix1D> func, final DoubleMatrix1D startPos) {
    final VectorFieldFirstOrderDifferentiator jac = new VectorFieldFirstOrderDifferentiator();
    return solve(observedValues, sigma, func, jac.differentiate(func), startPos);
  }

  /**
   * Use this when the model is given as a function of its parameters only (i.e. a function that takes a set of parameters and return a set of model values,
   * so the measurement points are already known to the function), and  analytic parameter sensitivity is available 
   * @param observedValues Set of measurement values
   * @param sigma Set of measurement errors
   * @param func The model as a function of its parameters only
   * @param jac The model sensitivity to its parameters (i.e. the Jacobian matrix) as a function of its parameters only
   * @param startPos  Initial value of the parameters 
   * @return value of the fitted parameters 
   */
  public LeastSquareResults solve(final DoubleMatrix1D observedValues, final DoubleMatrix1D sigma, final Function1D<DoubleMatrix1D, DoubleMatrix1D> func,
      final Function1D<DoubleMatrix1D, DoubleMatrix2D> jac, final DoubleMatrix1D startPos) {

    Validate.notNull(observedValues, "observedValues");
    Validate.notNull(sigma, " sigma");
    Validate.notNull(func, " func");
    Validate.notNull(jac, " jac");
    Validate.notNull(startPos, "startPos");
    final int n = observedValues.getNumberOfElements();
    Validate.isTrue(n == sigma.getNumberOfElements(), "observedValues and sigma must be same length");
    Validate.isTrue(n >= startPos.getNumberOfElements(), "must have data points greater or equal to number of parameters");

    DoubleMatrix1D theta = startPos;

    double lambda = 0.0; // if the model is linear, it will be solved in 1 step
    double newChiSqr, oldChiSqr;
    DoubleMatrix1D error = getError(func, observedValues, sigma, theta);
    DoubleMatrix1D newError;
    DoubleMatrix2D jacobian = getJacobian(jac, sigma, theta);

    oldChiSqr = getChiSqr(error);
    DoubleMatrix1D beta = getChiSqrGrad(error, jacobian);
    final double g0 = _algebra.getNorm2(beta);
    for (int count = 0; count < MAX_ATTEMPTS; count++) {
      DoubleMatrix2D alpha = getModifiedCurvatureMatrix(jacobian, lambda);

      DecompositionResult decmp;
      final DoubleMatrix1D deltaTheta;
      try {
        decmp = _decomposition.evaluate(alpha);
        deltaTheta = decmp.solve(beta);
      } catch (final Exception e) {
        throw new MathException(e);
      }

      final DoubleMatrix1D newTheta = (DoubleMatrix1D) _algebra.add(theta, deltaTheta);
      newError = getError(func, observedValues, sigma, newTheta);
      newChiSqr = getChiSqr(newError);
      if (newChiSqr < oldChiSqr) {
        lambda /= 10;
        theta = newTheta;
        error = newError;
        jacobian = getJacobian(jac, sigma, newTheta);
        beta = getChiSqrGrad(error, jacobian);

        // check for convergence
        if (_algebra.getNorm2(beta) < _eps * g0) {
          alpha = getModifiedCurvatureMatrix(jacobian, 0.0);
          decmp = _decomposition.evaluate(alpha);
          final DoubleMatrix2D covariance = decmp.solve(DoubleMatrixUtils.getIdentityMatrix2D(alpha.getNumberOfRows()));
          return new LeastSquareResults(newChiSqr, newTheta, covariance);
        }
        oldChiSqr = newChiSqr;
      } else {
        if (lambda == 0.0) { // this will happen the first time a full quadratic step fails
          lambda = 0.01;
        }
        lambda *= 10;
      }
    }
    throw new MathException("Could not converge in " + MAX_ATTEMPTS + " attempts");
  }

  private DoubleMatrix1D getError(final Function1D<DoubleMatrix1D, DoubleMatrix1D> func, final DoubleMatrix1D observedValues, final DoubleMatrix1D sigma, final DoubleMatrix1D theta) {
    final int n = observedValues.getNumberOfElements();
    final DoubleMatrix1D modelValues = func.evaluate(theta);
    Validate.isTrue(n == modelValues.getNumberOfElements(), "Number of data points different between model (" + modelValues.getNumberOfElements() + ") and observed (" + n + ")");
    final double[] res = new double[n];
    for (int i = 0; i < n; i++) {
      res[i] = (observedValues.getEntry(i) - modelValues.getEntry(i)) / sigma.getEntry(i);
    }

    return new DoubleMatrix1D(res);
  }

  private DoubleMatrix2D getJacobian(final Function1D<DoubleMatrix1D, DoubleMatrix2D> jac, final DoubleMatrix1D sigma, final DoubleMatrix1D theta) {
    final DoubleMatrix2D res = jac.evaluate(theta);
    final double[][] data = res.getData();
    final int m = res.getNumberOfRows();
    final int n = res.getNumberOfColumns();
    Validate.isTrue(theta.getNumberOfElements() == n, "Jacobian is wrong size");
    Validate.isTrue(sigma.getNumberOfElements() == m, "Jacobian is wrong size");

    for (int i = 0; i < m; i++) {
      for (int j = 0; j < n; j++) {
        data[i][j] /= sigma.getEntry(i);
      }
    }
    return res;
  }

  private double getChiSqr(final DoubleMatrix1D error) {
    return _algebra.getInnerProduct(error, error);
  }

  private DoubleMatrix1D getChiSqrGrad(final DoubleMatrix1D error, final DoubleMatrix2D jacobian) {
    return (DoubleMatrix1D) _algebra.multiply(error, jacobian);
  }

  private DoubleMatrix2D getModifiedCurvatureMatrix(final DoubleMatrix2D jacobian, final double lambda) {
    final int n = jacobian.getNumberOfRows();
    final int m = jacobian.getNumberOfColumns();

    final double[][] alpha = new double[m][m];

    for (int i = 0; i < m; i++) {
      double sum = 0.0;
      for (int k = 0; k < n; k++) {
        sum += FunctionUtils.square(jacobian.getEntry(k, i));
      }
      alpha[i][i] = (1 + lambda) * sum;

      for (int j = i + 1; j < m; j++) {
        sum = 0.0;
        for (int k = 0; k < n; k++) {
          sum += jacobian.getEntry(k, i) * jacobian.getEntry(k, j);
        }
        alpha[i][j] = sum;
        alpha[j][i] = sum;
      }
    }
    return new DoubleMatrix2D(alpha);
  }

}
