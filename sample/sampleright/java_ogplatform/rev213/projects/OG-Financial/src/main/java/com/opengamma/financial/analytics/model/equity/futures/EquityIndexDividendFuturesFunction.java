/**
 * Copyright (C) 2011 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.financial.analytics.model.equity.futures;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.time.calendar.Clock;
import javax.time.calendar.Period;
import javax.time.calendar.ZonedDateTime;

import org.apache.commons.lang.Validate;

import com.opengamma.OpenGammaRuntimeException;
import com.opengamma.analytics.financial.equity.future.definition.EquityFutureDefinition;
import com.opengamma.analytics.financial.equity.future.derivative.EquityFuture;
import com.opengamma.analytics.financial.equity.future.pricing.EquityFuturePricerFactory;
import com.opengamma.analytics.financial.equity.future.pricing.EquityFuturesPricer;
import com.opengamma.analytics.financial.equity.future.pricing.EquityFuturesPricingMethod;
import com.opengamma.analytics.financial.model.interestrate.curve.YieldAndDiscountCurve;
import com.opengamma.analytics.financial.simpleinstruments.pricing.SimpleFutureDataBundle;
import com.opengamma.core.historicaltimeseries.HistoricalTimeSeries;
import com.opengamma.core.security.Security;
import com.opengamma.core.value.MarketDataRequirementNames;
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
import com.opengamma.financial.analytics.conversion.EquityIndexDividendFutureSecurityConverter;
import com.opengamma.financial.analytics.timeseries.DateConstraint;
import com.opengamma.financial.analytics.timeseries.HistoricalTimeSeriesFunctionUtils;
import com.opengamma.financial.security.FinancialSecurityTypes;
import com.opengamma.financial.security.FinancialSecurityUtils;
import com.opengamma.financial.security.future.EquityIndexDividendFutureSecurity;
import com.opengamma.id.ExternalId;
import com.opengamma.id.ExternalIdBundle;
import com.opengamma.master.historicaltimeseries.HistoricalTimeSeriesResolutionResult;
import com.opengamma.master.historicaltimeseries.HistoricalTimeSeriesResolver;
import com.opengamma.util.money.Currency;

/**
 * This function will produce all valueRequirements that the EquityFutureSecurity offers. A trade may produce additional generic ones, e.g. date and number of contracts..
 */
public class EquityIndexDividendFuturesFunction extends AbstractFunction.NonCompiledInvoker {
  private static final String DIVIDEND_YIELD_FIELD = "EQY_DVD_YLD_EST";

  private final String _valueRequirementName;
  private final EquityFuturesPricingMethod _pricingMethod;
  private final String _fundingCurveName;
  private EquityIndexDividendFutureSecurityConverter _financialToAnalyticConverter;
  private final EquityFuturesPricer _pricer;
  private final String _pricingMethodName;

  /**
   * @param valueRequirementName String describes the value requested
   * @param pricingMethodName String corresponding to enum EquityFuturesPricingMethod {MARK_TO_MARKET or COST_OF_CARRY, DIVIDEND_YIELD}
   * @param fundingCurveName The name of the curve that will be used for discounting
   */
  public EquityIndexDividendFuturesFunction(final String valueRequirementName, final String pricingMethodName, final String fundingCurveName) {
    Validate.notNull(valueRequirementName, "value requirement name");
    Validate.notNull(pricingMethodName, "pricing method name");
    Validate.notNull(fundingCurveName, "funding curve name");
    Validate.isTrue(valueRequirementName.equals(ValueRequirementNames.PRESENT_VALUE)
            || valueRequirementName.equals(ValueRequirementNames.VALUE_RHO)
            || valueRequirementName.equals(ValueRequirementNames.PV01)
            || valueRequirementName.equals(ValueRequirementNames.VALUE_DELTA),
            "EquityFuturesFunction provides the following values PRESENT_VALUE, VALUE_DELTA, VALUE_RHO and PV01. Please choose one.");

    _valueRequirementName = valueRequirementName;

    Validate.isTrue(pricingMethodName.equals(EquityFuturePricerFactory.MARK_TO_MARKET)
                 || pricingMethodName.equals(EquityFuturePricerFactory.COST_OF_CARRY)
                 || pricingMethodName.equals(EquityFuturePricerFactory.DIVIDEND_YIELD),
        "OG-Analytics provides the following pricing methods for EquityFutureSecurity: MARK_TO_MARKET, DIVIDEND_YIELD and COST_OF_CARRY. Please choose one.");

    _pricingMethod = EquityFuturesPricingMethod.valueOf(pricingMethodName);
    _pricingMethodName = pricingMethodName;
    _fundingCurveName = fundingCurveName;
    _pricer = EquityFuturePricerFactory.getMethod(pricingMethodName);
  }

  @Override
  public void init(final FunctionCompilationContext context) {
    _financialToAnalyticConverter = new EquityIndexDividendFutureSecurityConverter();
  }

  @Override
  /**
   * @param target The HbComputationTargetSpecification is a TradeImpl
   */
  public Set<ComputedValue> execute(FunctionExecutionContext executionContext, FunctionInputs inputs, ComputationTarget target, Set<ValueRequirement> desiredValues) {
    final Clock snapshotClock = executionContext.getValuationClock();
    final ZonedDateTime now = snapshotClock.zonedDateTime();
    final EquityIndexDividendFutureSecurity security = (EquityIndexDividendFutureSecurity) target.getSecurity();

    final ZonedDateTime valuationTime = executionContext.getValuationClock().zonedDateTime();

    //    final Double lastMarginPrice = getLatestValueFromTimeSeries(HistoricalTimeSeriesFields.LAST_PRICE, executionContext, security.getExternalIdBundle(), now);
    //    trade.setPremium(lastMarginPrice); // TODO !!! Issue of futures and margining

    // Build the analytic's version of the security - the derivative    
    final EquityFutureDefinition definition = _financialToAnalyticConverter.visitEquityIndexDividendFutureSecurity(security);
    final EquityFuture derivative = definition.toDerivative(valuationTime);

    // Build the DataBundle it requires
    final SimpleFutureDataBundle dataBundle;
    switch (_pricingMethod) {
      case MARK_TO_MARKET:
        Double marketPrice = getMarketPrice(security, inputs);
        dataBundle = new SimpleFutureDataBundle(null, marketPrice, null, null, null);
        break;
      case COST_OF_CARRY:
        Double costOfCarry = getCostOfCarry(security, inputs);
        Double spotUnderlyer = getSpot(security, inputs);
        dataBundle = new SimpleFutureDataBundle(null, null, spotUnderlyer, null, costOfCarry);
        break;
      case DIVIDEND_YIELD:
        Double spot = getSpot(security, inputs);
        HistoricalTimeSeries hts = (HistoricalTimeSeries) inputs.getValue(ValueRequirementNames.HISTORICAL_TIME_SERIES);
        Double dividendYield = hts.getTimeSeries().getLatestValue();
        dividendYield /= 100.0;
        YieldAndDiscountCurve fundingCurve = getYieldCurve(security, inputs);
        dataBundle = new SimpleFutureDataBundle(fundingCurve, null, spot, dividendYield, null);
        break;
      default:
        throw new OpenGammaRuntimeException("Unhandled pricingMethod");
    }

    // Call OG-Analytics
    return getComputedValue(target.toSpecification(), derivative, dataBundle, security);
  }

  /**
   * Given _valueRequirement and _pricingMethod supplied, this calls to OG-Analytics.
   * 
   * @return Call to the Analytics to get the value required
   */
  private Set<ComputedValue> getComputedValue(ComputationTargetSpecification targetSpec, EquityFuture derivative, SimpleFutureDataBundle bundle, EquityIndexDividendFutureSecurity security) {

    final double nContracts = 1;
    final double valueItself;

    final ValueSpecification specification = getValueSpecification(_valueRequirementName, targetSpec, security);

    if (_valueRequirementName.equals(ValueRequirementNames.PRESENT_VALUE)) {
      valueItself = _pricer.presentValue(derivative, bundle);
    } else if (_valueRequirementName.equals(ValueRequirementNames.VALUE_DELTA)) {
      valueItself = _pricer.spotDelta(derivative, bundle);
    } else if (_valueRequirementName.equals(ValueRequirementNames.VALUE_RHO)) {
      valueItself = _pricer.ratesDelta(derivative, bundle);
    } else if (_valueRequirementName.equals(ValueRequirementNames.PV01)) {
      valueItself = _pricer.pv01(derivative, bundle);
    } else {
      throw new OpenGammaRuntimeException("_valueRequirementName," + _valueRequirementName + ", unexpected. Should have been recognized in the constructor.");
    }
    return Collections.singleton(new ComputedValue(specification, nContracts * valueItself));

  }

  @Override
  public ComputationTargetType getTargetType() {
    return FinancialSecurityTypes.EQUITY_INDEX_DIVIDEND_FUTURE_SECURITY;
  }

  @Override
  public Set<ValueRequirement> getRequirements(FunctionCompilationContext context, ComputationTarget target, ValueRequirement desiredValue) {
    final EquityIndexDividendFutureSecurity security = (EquityIndexDividendFutureSecurity) target.getSecurity();
    final Set<ValueRequirement> requirements = new HashSet<ValueRequirement>();
    ValueRequirement requirement;
    switch (_pricingMethod) {
      case MARK_TO_MARKET:
        requirements.add(getMarketPriceRequirement(security));
        break;
      case COST_OF_CARRY:
        requirements.add(getSpotAssetRequirement(security));
        requirements.add(getCostOfCarryRequirement(security));
        break;
      case DIVIDEND_YIELD:
        requirements.add(getSpotAssetRequirement(security));
        requirements.add(getDiscountCurveRequirement(security));
        requirement = getDividendYieldRequirement(context, security);
        if (requirement == null) {
          return null;
        }
        requirements.add(requirement);
        break;
      default:
        throw new OpenGammaRuntimeException("Unhandled _pricingMethod=" + _pricingMethod);
    }
    return requirements;
  }

  private ValueRequirement getDiscountCurveRequirement(EquityIndexDividendFutureSecurity security) {
    ValueProperties properties = ValueProperties.builder().with(ValuePropertyNames.CURVE, _fundingCurveName).get();
    return new ValueRequirement(ValueRequirementNames.YIELD_CURVE, ComputationTargetSpecification.of(security.getCurrency()), properties);
  }

  private YieldAndDiscountCurve getYieldCurve(EquityIndexDividendFutureSecurity security, FunctionInputs inputs) {

    final ValueRequirement curveRequirement = getDiscountCurveRequirement(security);
    final Object curveObject = inputs.getValue(curveRequirement);
    if (curveObject == null) {
      throw new OpenGammaRuntimeException("Could not get " + curveRequirement);
    }
    return (YieldAndDiscountCurve) curveObject;
  }

  private ValueRequirement getDividendYieldRequirement(EquityIndexDividendFutureSecurity security) {
    ExternalId id = security.getUnderlyingId();
    return new ValueRequirement(MarketDataRequirementNames.DIVIDEND_YIELD, ComputationTargetType.PRIMITIVE, id);
  }

  @SuppressWarnings("unused")
  private Double getDividendYield(EquityIndexDividendFutureSecurity security, FunctionInputs inputs) {
    ValueRequirement dividendRequirement = getDividendYieldRequirement(security);
    final Object dividendObject = inputs.getValue(dividendRequirement);
    if (dividendObject == null) {
      throw new OpenGammaRuntimeException("Could not get " + dividendRequirement);
    }
    return (Double) dividendObject;
  }

  private ValueRequirement getSpotAssetRequirement(EquityIndexDividendFutureSecurity security) {
    ValueRequirement req = new ValueRequirement(MarketDataRequirementNames.MARKET_VALUE, ComputationTargetType.PRIMITIVE, security.getUnderlyingId());
    return req;
  }

  private Double getSpot(EquityIndexDividendFutureSecurity security, FunctionInputs inputs) {
    ValueRequirement spotRequirement = getSpotAssetRequirement(security);
    final Object spotObject = inputs.getValue(spotRequirement);
    if (spotObject == null) {
      throw new OpenGammaRuntimeException("Could not get " + spotRequirement);
    }
    return (Double) spotObject;
  }

  private ValueRequirement getCostOfCarryRequirement(EquityIndexDividendFutureSecurity security) {
    return new ValueRequirement(MarketDataRequirementNames.COST_OF_CARRY, ComputationTargetType.PRIMITIVE, security.getUnderlyingId());
  }

  private Double getCostOfCarry(EquityIndexDividendFutureSecurity security, FunctionInputs inputs) {
    ValueRequirement costOfCarryRequirement = getCostOfCarryRequirement(security);
    final Object costOfCarryObject = inputs.getValue(costOfCarryRequirement);
    if (costOfCarryObject == null) {
      throw new OpenGammaRuntimeException("Could not get " + costOfCarryRequirement);
    }
    return (Double) costOfCarryObject;
  }

  private ValueRequirement getMarketPriceRequirement(EquityIndexDividendFutureSecurity security) {
    return new ValueRequirement(MarketDataRequirementNames.MARKET_VALUE, ComputationTargetType.SECURITY, security.getUniqueId());
  }

  private Double getMarketPrice(EquityIndexDividendFutureSecurity security, FunctionInputs inputs) {
    ValueRequirement marketPriceRequirement = getMarketPriceRequirement(security);
    final Object marketPriceObject = inputs.getValue(marketPriceRequirement);
    if (marketPriceObject == null) {
      throw new OpenGammaRuntimeException("Could not get " + marketPriceRequirement);
    }
    return (Double) marketPriceObject;
  }

  private ValueRequirement getDividendYieldRequirement(final FunctionCompilationContext context, final EquityIndexDividendFutureSecurity security) {
    final HistoricalTimeSeriesResolver resolver = OpenGammaCompilationContext.getHistoricalTimeSeriesResolver(context);
    final HistoricalTimeSeriesResolutionResult timeSeries = resolver.resolve(ExternalIdBundle.of(security.getUnderlyingId()), null, null, null, DIVIDEND_YIELD_FIELD, null);
    if (timeSeries == null) {
      return null;
    }
    return HistoricalTimeSeriesFunctionUtils.createHTSRequirement(timeSeries, DIVIDEND_YIELD_FIELD,
        DateConstraint.VALUATION_TIME.minus(Period.ofDays(7)), true, DateConstraint.VALUATION_TIME, true);
  }

  @Override
  public Set<ValueSpecification> getResults(FunctionCompilationContext context, ComputationTarget target) {
    return Collections.singleton(getValueSpecification(_valueRequirementName, target.toSpecification(), target.getSecurity()));
  }

  /**
   * Create a ValueSpecification, the meta data for the value itself.
   * 
   * @param valueRequirementName the value requirement name
   * @param targetSpec the target specification
   * @param equityFutureSecurity The OG_Financial Security
   */
  private ValueSpecification getValueSpecification(final String valueRequirementName, final ComputationTargetSpecification targetSpec, final Security equityFutureSecurity) {
    final Currency ccy = FinancialSecurityUtils.getCurrency(equityFutureSecurity);
    final ValueProperties.Builder properties = createValueProperties();
    final ValueProperties valueProps = properties
        .with(ValuePropertyNames.CURRENCY, ccy.getCode())
        .with(ValuePropertyNames.CURVE_CURRENCY, ccy.getCode())
        .with(ValuePropertyNames.CURVE, _fundingCurveName)
        .with(ValuePropertyNames.CALCULATION_METHOD, _pricingMethodName)
        .get();

    return new ValueSpecification(valueRequirementName, targetSpec, valueProps);
  }
}
