/**
 * Copyright (C) 2012 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.financial.analytics.model.sabrcube;

import com.opengamma.OpenGammaRuntimeException;
import com.opengamma.analytics.financial.interestrate.PresentValueCurveSensitivitySABRCalculator;
import com.opengamma.analytics.financial.interestrate.PresentValueNodeSensitivityCalculator;
import com.opengamma.analytics.financial.interestrate.YieldCurveBundle;
import com.opengamma.analytics.financial.model.option.definition.SABRInterestRateDataBundle;
import com.opengamma.analytics.financial.model.option.definition.SABRInterestRateParameters;
import com.opengamma.analytics.financial.model.volatility.smile.function.VolatilityFunctionFactory;
import com.opengamma.analytics.math.surface.InterpolatedDoublesSurface;
import com.opengamma.core.security.Security;
import com.opengamma.engine.ComputationTarget;
import com.opengamma.engine.ComputationTargetType;
import com.opengamma.engine.function.FunctionCompilationContext;
import com.opengamma.engine.function.FunctionInputs;
import com.opengamma.engine.value.ValueProperties;
import com.opengamma.engine.value.ValuePropertyNames;
import com.opengamma.engine.value.ValueRequirement;
import com.opengamma.financial.analytics.conversion.SwapSecurityUtils;
import com.opengamma.financial.analytics.fixedincome.InterestRateInstrumentType;
import com.opengamma.financial.analytics.model.volatility.SmileFittingProperties;
import com.opengamma.financial.analytics.volatility.fittedresults.SABRFittedSurfaces;
import com.opengamma.financial.convention.daycount.DayCount;
import com.opengamma.financial.security.FinancialSecurityUtils;
import com.opengamma.financial.security.capfloor.CapFloorSecurity;
import com.opengamma.financial.security.option.SwaptionSecurity;
import com.opengamma.financial.security.swap.SwapSecurity;
import com.opengamma.util.money.Currency;

/**
 *
 */
public class SABRNoExtrapolationYCNSFunction extends SABRYCNSFunction {
  private static final PresentValueNodeSensitivityCalculator NSC = PresentValueNodeSensitivityCalculator.using(PresentValueCurveSensitivitySABRCalculator.getInstance());

  @Override
  public boolean canApplyTo(final FunctionCompilationContext context, final ComputationTarget target) {
    if (target.getType() != ComputationTargetType.SECURITY) {
      return false;
    }
    final Security security = target.getSecurity();
    return security instanceof SwaptionSecurity
        || (security instanceof SwapSecurity && (SwapSecurityUtils.getSwapType(((SwapSecurity) security)) == InterestRateInstrumentType.SWAP_FIXED_CMS
        || SwapSecurityUtils.getSwapType(((SwapSecurity) security)) == InterestRateInstrumentType.SWAP_CMS_CMS
        || SwapSecurityUtils.getSwapType(((SwapSecurity) security)) == InterestRateInstrumentType.SWAP_IBOR_CMS))
        || security instanceof CapFloorSecurity;
  }

  @Override
  protected SABRInterestRateDataBundle getModelParameters(final ComputationTarget target, final FunctionInputs inputs, final Currency currency, final DayCount dayCount,
      final YieldCurveBundle yieldCurves, final ValueRequirement desiredValue) {
    final String cubeName = desiredValue.getConstraint(ValuePropertyNames.CUBE);
    final String fittingMethod = desiredValue.getConstraint(SmileFittingProperties.PROPERTY_FITTING_METHOD);
    final ValueRequirement surfacesRequirement = getCubeRequirement(cubeName, currency, fittingMethod);
    final Object surfacesObject = inputs.getValue(surfacesRequirement);
    if (surfacesObject == null) {
      throw new OpenGammaRuntimeException("Could not get " + surfacesRequirement);
    }
    final SABRFittedSurfaces surfaces = (SABRFittedSurfaces) surfacesObject;
    final InterpolatedDoublesSurface alphaSurface = surfaces.getAlphaSurface();
    final InterpolatedDoublesSurface betaSurface = surfaces.getBetaSurface();
    final InterpolatedDoublesSurface nuSurface = surfaces.getNuSurface();
    final InterpolatedDoublesSurface rhoSurface = surfaces.getRhoSurface();
    final SABRInterestRateParameters modelParameters = new SABRInterestRateParameters(alphaSurface, betaSurface, rhoSurface, nuSurface, dayCount, VolatilityFunctionFactory.HAGAN_FORMULA);
    return new SABRInterestRateDataBundle(modelParameters, yieldCurves);
  }

  @Override
  protected ValueProperties getResultProperties(final Currency currency) {
    return createValueProperties()
        .with(ValuePropertyNames.CURRENCY, currency.getCode())
        .with(ValuePropertyNames.CURVE_CURRENCY, currency.getCode())
        .withAny(ValuePropertyNames.CURVE_CALCULATION_CONFIG)
        .withAny(ValuePropertyNames.CURVE)
        .withAny(ValuePropertyNames.CUBE)
        .withAny(SmileFittingProperties.PROPERTY_FITTING_METHOD)
        .with(SmileFittingProperties.PROPERTY_VOLATILITY_MODEL, SmileFittingProperties.SABR)
        .with(ValuePropertyNames.CALCULATION_METHOD, SABRFunction.SABR_NO_EXTRAPOLATION).get();
  }

  @Override
  protected ValueProperties getResultProperties(final ComputationTarget target, final ValueRequirement desiredValue) {
    final String currency = FinancialSecurityUtils.getCurrency(target.getSecurity()).getCode();
    final String curveCalculationConfig = desiredValue.getConstraint(ValuePropertyNames.CURVE_CALCULATION_CONFIG);
    final String curveName = desiredValue.getConstraint(ValuePropertyNames.CURVE);
    final String fittingMethod = desiredValue.getConstraint(SmileFittingProperties.PROPERTY_FITTING_METHOD);
    final String cubeName = desiredValue.getConstraint(ValuePropertyNames.CUBE);
    return createValueProperties()
        .with(ValuePropertyNames.CURRENCY, currency)
        .with(ValuePropertyNames.CURVE_CURRENCY, currency)
        .with(ValuePropertyNames.CURVE_CALCULATION_CONFIG, curveCalculationConfig)
        .with(ValuePropertyNames.CURVE, curveName)
        .with(ValuePropertyNames.CUBE, cubeName)
        .with(SmileFittingProperties.PROPERTY_FITTING_METHOD, fittingMethod)
        .with(SmileFittingProperties.PROPERTY_VOLATILITY_MODEL, SmileFittingProperties.SABR)
        .with(ValuePropertyNames.CALCULATION_METHOD, SABRFunction.SABR_NO_EXTRAPOLATION).get();
  }

  @Override
  protected PresentValueNodeSensitivityCalculator getNodeSensitivityCalculator(final ValueRequirement desiredValue) {
    return NSC;
  }

}
