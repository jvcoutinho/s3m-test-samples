/**
 * Copyright (C) 2011 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.financial.analytics.model.irfutureoption;

import java.util.HashSet;
import java.util.Set;

import javax.time.calendar.Clock;
import javax.time.calendar.ZonedDateTime;

import org.apache.commons.lang.Validate;

import com.google.common.collect.Sets;
import com.opengamma.OpenGammaRuntimeException;
import com.opengamma.analytics.financial.instrument.InstrumentDefinition;
import com.opengamma.analytics.financial.interestrate.AbstractInstrumentDerivativeVisitor;
import com.opengamma.analytics.financial.interestrate.InstrumentDerivative;
import com.opengamma.analytics.financial.interestrate.YieldCurveBundle;
import com.opengamma.analytics.financial.interestrate.future.derivative.InterestRateFuture;
import com.opengamma.analytics.financial.interestrate.future.derivative.InterestRateFutureOptionMarginSecurity;
import com.opengamma.analytics.financial.interestrate.future.derivative.InterestRateFutureOptionMarginTransaction;
import com.opengamma.analytics.financial.interestrate.future.derivative.InterestRateFutureOptionPremiumSecurity;
import com.opengamma.analytics.financial.interestrate.future.derivative.InterestRateFutureOptionPremiumTransaction;
import com.opengamma.analytics.financial.interestrate.future.method.InterestRateFutureDiscountingMethod;
import com.opengamma.analytics.financial.model.interestrate.curve.YieldAndDiscountCurve;
import com.opengamma.analytics.financial.model.option.pricing.analytic.formula.BlackFunctionData;
import com.opengamma.analytics.financial.model.option.pricing.analytic.formula.EuropeanVanillaOption;
import com.opengamma.analytics.financial.model.option.pricing.fourier.FourierPricer;
import com.opengamma.analytics.financial.model.option.pricing.fourier.HestonCharacteristicExponent;
import com.opengamma.analytics.math.integration.RungeKuttaIntegrator1D;
import com.opengamma.analytics.math.surface.InterpolatedDoublesSurface;
import com.opengamma.core.holiday.HolidaySource;
import com.opengamma.core.position.Trade;
import com.opengamma.core.position.impl.SimpleTrade;
import com.opengamma.core.region.RegionSource;
import com.opengamma.core.security.SecuritySource;
import com.opengamma.engine.ComputationTarget;
import com.opengamma.engine.ComputationTargetSpecification;
import com.opengamma.engine.function.AbstractFunction;
import com.opengamma.engine.function.FunctionCompilationContext;
import com.opengamma.engine.function.FunctionExecutionContext;
import com.opengamma.engine.function.FunctionInputs;
import com.opengamma.engine.target.ComputationTargetType;
import com.opengamma.engine.value.ComputedValue;
import com.opengamma.engine.value.ValueProperties;
import com.opengamma.engine.value.ValuePropertyNames;
import com.opengamma.engine.value.ValueRequirement;
import com.opengamma.engine.value.ValueRequirementNames;
import com.opengamma.engine.value.ValueSpecification;
import com.opengamma.financial.OpenGammaCompilationContext;
import com.opengamma.financial.analytics.conversion.FixedIncomeConverterDataProvider;
import com.opengamma.financial.analytics.conversion.InterestRateFutureOptionSecurityConverter;
import com.opengamma.financial.analytics.conversion.InterestRateFutureOptionTradeConverter;
import com.opengamma.financial.analytics.ircurve.YieldCurveFunction;
import com.opengamma.financial.analytics.model.InstrumentTypeProperties;
import com.opengamma.financial.analytics.timeseries.HistoricalTimeSeriesBundle;
import com.opengamma.financial.analytics.timeseries.HistoricalTimeSeriesFunctionUtils;
import com.opengamma.financial.analytics.volatility.fittedresults.HestonFittedSurfaces;
import com.opengamma.financial.convention.ConventionBundleSource;
import com.opengamma.financial.security.FinancialSecurityUtils;
import com.opengamma.financial.security.option.IRFutureOptionSecurity;
import com.opengamma.master.historicaltimeseries.HistoricalTimeSeriesResolver;
import com.opengamma.util.money.Currency;

/**
 * @deprecated Uses old properties
 */
@Deprecated
public class InterestRateFutureOptionHestonPresentValueFunction extends AbstractFunction.NonCompiledInvoker {
  private final String _forwardCurveName;
  private final String _fundingCurveName;
  private final String _surfaceName;
  private InterestRateFutureOptionTradeConverter _converter;
  private FixedIncomeConverterDataProvider _dataConverter;

  public InterestRateFutureOptionHestonPresentValueFunction(final String forwardCurveName, final String fundingCurveName, final String surfaceName) {
    Validate.notNull(forwardCurveName, "forward curve name");
    Validate.notNull(fundingCurveName, "funding curve name");
    Validate.notNull(surfaceName, "surface name");
    _forwardCurveName = forwardCurveName;
    _fundingCurveName = fundingCurveName;
    _surfaceName = surfaceName;
  }

  @Override
  public void init(final FunctionCompilationContext context) {
    final HolidaySource holidaySource = OpenGammaCompilationContext.getHolidaySource(context);
    final RegionSource regionSource = OpenGammaCompilationContext.getRegionSource(context);
    final ConventionBundleSource conventionSource = OpenGammaCompilationContext.getConventionBundleSource(context);
    final SecuritySource securitySource = OpenGammaCompilationContext.getSecuritySource(context);
    final HistoricalTimeSeriesResolver timeSeriesResolver = OpenGammaCompilationContext.getHistoricalTimeSeriesResolver(context);
    _converter = new InterestRateFutureOptionTradeConverter(new InterestRateFutureOptionSecurityConverter(holidaySource, conventionSource, regionSource, securitySource));
    _dataConverter = new FixedIncomeConverterDataProvider(conventionSource, timeSeriesResolver);
  }

  @Override
  public Set<ComputedValue> execute(final FunctionExecutionContext executionContext, final FunctionInputs inputs, final ComputationTarget target, final Set<ValueRequirement> desiredValues) {
    final Clock snapshotClock = executionContext.getValuationClock();
    final ZonedDateTime now = snapshotClock.zonedDateTime();
    final HistoricalTimeSeriesBundle timeSeries = HistoricalTimeSeriesFunctionUtils.getHistoricalTimeSeriesInputs(executionContext, inputs);
    final SimpleTrade trade = (SimpleTrade) target.getTrade();
    final InstrumentDefinition<InstrumentDerivative> irFutureOptionDefinition = (InstrumentDefinition<InstrumentDerivative>) _converter.convert(trade);
    final InstrumentDerivative irFutureOption = _dataConverter.convert(trade.getSecurity(), irFutureOptionDefinition, now, new String[] {_fundingCurveName, _forwardCurveName }, timeSeries);
    final double price = irFutureOption.accept(new MyDerivativeVisitor(target, inputs));
    return Sets.newHashSet(new ComputedValue(getSpecification(target), price));
  }

  @Override
  public ComputationTargetType getTargetType() {
    return ComputationTargetType.TRADE;
  }

  @Override
  public boolean canApplyTo(final FunctionCompilationContext context, final ComputationTarget target) {
    return target.getTrade().getSecurity() instanceof IRFutureOptionSecurity;
  }

  @Override
  public Set<ValueRequirement> getRequirements(final FunctionCompilationContext context, final ComputationTarget target, final ValueRequirement desiredValue) {
    final Set<ValueRequirement> requirements = new HashSet<ValueRequirement>();
    requirements.add(getSurfaceRequirement(target));
    if (_forwardCurveName.equals(_fundingCurveName)) {
      requirements.add(getCurveRequirement(target, _forwardCurveName, null, null));
      return requirements;
    }
    requirements.add(getCurveRequirement(target, _forwardCurveName, _forwardCurveName, _fundingCurveName));
    requirements.add(getCurveRequirement(target, _fundingCurveName, _forwardCurveName, _fundingCurveName));
    final Trade trade = target.getTrade();
    final Set<ValueRequirement> timeSeriesRequirements = _dataConverter.getConversionTimeSeriesRequirements(trade.getSecurity(), _converter.convert(trade));
    if (timeSeriesRequirements == null) {
      return null;
    }
    requirements.addAll(timeSeriesRequirements);
    return requirements;
  }

  @Override
  public Set<ValueSpecification> getResults(final FunctionCompilationContext context, final ComputationTarget target) {
    return Sets.newHashSet(getSpecification(target));
  }

  private ValueRequirement getSurfaceRequirement(final ComputationTarget target) {
    final Currency currency = FinancialSecurityUtils.getCurrency(target.getTrade().getSecurity());
    final ValueProperties properties = ValueProperties.with(ValuePropertyNames.CURRENCY, currency.getCode()).with(ValuePropertyNames.SURFACE, _surfaceName)
        .with(InstrumentTypeProperties.PROPERTY_SURFACE_INSTRUMENT_TYPE, InstrumentTypeProperties.IR_FUTURE_OPTION).get();
    return new ValueRequirement(ValueRequirementNames.HESTON_SURFACES, ComputationTargetSpecification.of(currency), properties);
  }

  private ValueSpecification getSpecification(final ComputationTarget target) {
    return new ValueSpecification(ValueRequirementNames.PRESENT_VALUE, target.toSpecification(), createValueProperties()
        .with(ValuePropertyNames.CURRENCY, FinancialSecurityUtils.getCurrency(target.getTrade().getSecurity()).getCode())
        .with(YieldCurveFunction.PROPERTY_FORWARD_CURVE, _forwardCurveName)
        .with(YieldCurveFunction.PROPERTY_FUNDING_CURVE, _fundingCurveName).with(ValuePropertyNames.SURFACE, _surfaceName)
        .with(ValuePropertyNames.SMILE_FITTING_METHOD, "Heston")
        .with(ValuePropertyNames.CALCULATION_METHOD, "Fourier").get());
  }

  private ValueRequirement getCurveRequirement(final ComputationTarget target, final String curveName, final String advisoryForward, final String advisoryFunding) {
    return YieldCurveFunction.getCurveRequirement(FinancialSecurityUtils.getCurrency(target.getTrade().getSecurity()), curveName, advisoryForward, advisoryFunding);
  }

  private class MyDerivativeVisitor extends AbstractInstrumentDerivativeVisitor<YieldCurveBundle, Double> {
    private final double _alpha = -0.5;
    private final double _tolerance = 0.001;
    private final InterestRateFutureDiscountingMethod _futurePricer = InterestRateFutureDiscountingMethod.getInstance();
    private final FourierPricer _fourierPricer = new FourierPricer(new RungeKuttaIntegrator1D());
    private final ComputationTarget _target;
    private final FunctionInputs _inputs;

    public MyDerivativeVisitor(final ComputationTarget target, final FunctionInputs inputs) {
      _target = target;
      _inputs = inputs;
    }

    @Override
    public Double visitInterestRateFutureOptionPremiumSecurity(final InterestRateFutureOptionPremiumSecurity option) {
      final double t = option.getExpirationTime();
      final double k = option.getStrike();
      final boolean isCall = option.isCall();
      final InterestRateFuture irFuture = option.getUnderlyingFuture();
      final double f = 1 - _futurePricer.price(irFuture, getYieldCurves(_target, _inputs));
      final BlackFunctionData blackData = new BlackFunctionData(f, 1, 0);
      final EuropeanVanillaOption vanillaOption = new EuropeanVanillaOption(k, t, isCall);
      final HestonCharacteristicExponent ce = getModelParameters(_target, _inputs, t, k);
      return _fourierPricer.price(blackData, vanillaOption, ce, _alpha, _tolerance, true);
    }

    @Override
    public Double visitInterestRateFutureOptionPremiumTransaction(final InterestRateFutureOptionPremiumTransaction option) {
      return visitInterestRateFutureOptionPremiumSecurity(option.getUnderlyingOption());
    }

    @Override
    public Double visitInterestRateFutureOptionMarginSecurity(final InterestRateFutureOptionMarginSecurity option) {
      final double t = option.getExpirationTime();
      final double k = option.getStrike();
      final boolean isCall = option.isCall();
      final InterestRateFuture irFuture = option.getUnderlyingFuture();
      final double f = 1 - _futurePricer.price(irFuture, getYieldCurves(_target, _inputs));
      final BlackFunctionData blackData = new BlackFunctionData(f, 1, 1e-6);
      final EuropeanVanillaOption vanillaOption = new EuropeanVanillaOption(k, t, isCall);
      final HestonCharacteristicExponent ce = getModelParameters(_target, _inputs, t, k);
      return _fourierPricer.price(blackData, vanillaOption, ce, _alpha, _tolerance, true);
    }

    @Override
    public Double visitInterestRateFutureOptionMarginTransaction(final InterestRateFutureOptionMarginTransaction option) {
      return visitInterestRateFutureOptionMarginSecurity(option.getUnderlyingOption());
    }

    @SuppressWarnings("synthetic-access")
    private YieldCurveBundle getYieldCurves(final ComputationTarget target, final FunctionInputs inputs) {
      final ValueRequirement forwardCurveRequirement = getCurveRequirement(target, _forwardCurveName, null, null);
      final Object forwardCurveObject = inputs.getValue(forwardCurveRequirement);
      if (forwardCurveObject == null) {
        throw new OpenGammaRuntimeException("Could not get " + forwardCurveRequirement);
      }
      Object fundingCurveObject = null;
      if (!_forwardCurveName.equals(_fundingCurveName)) {
        final ValueRequirement fundingCurveRequirement = getCurveRequirement(target, _fundingCurveName, null, null);
        fundingCurveObject = inputs.getValue(fundingCurveRequirement);
        if (fundingCurveObject == null) {
          throw new OpenGammaRuntimeException("Could not get " + fundingCurveRequirement);
        }
      }
      final YieldAndDiscountCurve forwardCurve = (YieldAndDiscountCurve) forwardCurveObject;
      final YieldAndDiscountCurve fundingCurve = fundingCurveObject == null ? forwardCurve : (YieldAndDiscountCurve) fundingCurveObject;
      return new YieldCurveBundle(new String[] {_fundingCurveName, _forwardCurveName }, new YieldAndDiscountCurve[] {fundingCurve, forwardCurve });
    }

    private HestonCharacteristicExponent getModelParameters(final ComputationTarget target, final FunctionInputs inputs, final double t, final double k) {
      final Currency currency = FinancialSecurityUtils.getCurrency(target.getTrade().getSecurity());
      @SuppressWarnings("synthetic-access")
      final ValueRequirement surfacesRequirement = getSurfaceRequirement(target);
      final Object surfacesObject = inputs.getValue(surfacesRequirement);
      if (surfacesObject == null) {
        throw new OpenGammaRuntimeException("Could not get " + surfacesRequirement);
      }
      final HestonFittedSurfaces surfaces = (HestonFittedSurfaces) surfacesObject;
      if (!surfaces.getCurrency().equals(currency)) {
        throw new OpenGammaRuntimeException("Don't know how this happened");
      }
      final InterpolatedDoublesSurface kappaSurface = surfaces.getKappaSurface();
      final InterpolatedDoublesSurface thetaSurface = surfaces.getThetaSurface();
      final InterpolatedDoublesSurface vol0Surface = surfaces.getVol0Surface();
      final InterpolatedDoublesSurface omegaSurface = surfaces.getOmegaSurface();
      final InterpolatedDoublesSurface rhoSurface = surfaces.getRhoSurface();
      return new HestonCharacteristicExponent(kappaSurface.getZValue(t, k), thetaSurface.getZValue(t, k), vol0Surface.getZValue(t, k), omegaSurface.getZValue(t, k), rhoSurface.getZValue(t, k));
    }
  }
}
