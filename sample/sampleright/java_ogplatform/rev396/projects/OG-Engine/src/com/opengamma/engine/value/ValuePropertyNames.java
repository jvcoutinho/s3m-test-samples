/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.engine.value;

import com.opengamma.engine.function.FunctionDefinition;
import com.opengamma.util.PublicAPI;

/**
 * Standard names used to refer to particular value properties.
 * <p>
 * These name are used as keys to define specific value properties for the engine.
 * They should be used by a {@link FunctionDefinition} to annotate their requirements
 * or provide additional context about their outputs.
 */
@PublicAPI
public final class ValuePropertyNames {

  /**
   * Restricted constructor.
   */
  private ValuePropertyNames() {
  }

  /**
   * The aggregation approach used to produce the output value from the inputs. 
   * <p>
   * This should be used where there are multiple possible behaviors from functions to produce an aggregate
   * value. For example, input values that cannot be combined directly might either be discarded or converted;
   * this property will indicate which. Note that in such circumstances the priority of each function in the
   * repository is important to force "default" behaviors if no explicit aggregation constraint is made
   * when requesting the produced value
   */
  public static final String AGGREGATION = "Aggregation";

  /**
   * The country of a value, specified as an ISO 2 digit code.
   * <p>
   * This should only be used where it is meaningful to describe a value with a single country
   * instead of a currency (e.g. a bond issued by a euro-zone country).
   */
  public static final String COUNTRY = "Country";

  /**
   * The symbolic name of a volatility cube used to produce a value, valid within the naming context of
   * the function repository containing the function definition used.
   * <p>
   * This should only be used where it is meaningful to describe a value based on a single
   * cube. If produced by functions requiring multiple cube inputs, a suitable prefix should
   * be used to differentiate the input cubes.
   */
  public static final String CUBE = "Cube";

  /**
   * The currency of a value, specified as an ISO code.
   * <p>
   * This should only be used where it is meaningful to describe a value with a single currency.
   * For example, an exchange rate should not make use of this property.
   */
  public static final String CURRENCY = "Currency";

  /**
   * The symbolic name of a curve used to produce a value, valid within the naming context of
   * the function repository containing the function definition used.
   * <p>
   * This should only be used where it is meaningful to describe a value based on a single
   * curve. If produced by functions requiring multiple curve inputs, a suitable prefix should
   * be used to differentiate the input curves.
   */
  public static final String CURVE = "Curve";
  
  /**
   * The currency of the curve used to produce a value. This does not necessarily imply anything about the currency
   * of the output value.
   */
  public static final String CURVE_CURRENCY = "CurveCurrency";

  /**
   * The function identifier that produced a value.
   * <p>
   * If there are multiple functions in a repository that can compute a given value, this can
   * be used as a constraint to force a particular one to be used.
   * <p>
   * The result {@link ValueSpecification} objects created by functions must always include an
   * appropriate function identifier.
   */
  public static final String FUNCTION = "Function";

  /**
   * The symbolic name of a surface used to produce a value, valid within the naming context of
   * the function repository containing the function definition used.
   * <p>
   * This should only be used where it is meaningful to describe a value based on a single
   * surface. If produced by functions requiring multiple surface inputs, a suitable prefix should
   * be used to differentiate the input surfaces.
   */
  public static final String SURFACE = "Surface";

}
