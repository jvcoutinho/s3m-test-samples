/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.web.spring;

import static com.opengamma.core.historicaltimeseries.HistoricalTimeSeriesFields.LAST_PRICE;
import static com.opengamma.master.historicaltimeseries.impl.HistoricalTimeSeriesRatingFieldNames.DEFAULT_CONFIG_NAME;

import java.io.OutputStreamWriter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.fudgemsg.FudgeContext;
import org.fudgemsg.FudgeMsg;
import org.fudgemsg.FudgeMsgFormatter;
import org.fudgemsg.wire.FudgeMsgWriter;
import org.fudgemsg.wire.xml.FudgeXMLSettings;
import org.fudgemsg.wire.xml.FudgeXMLStreamWriter;

import com.opengamma.engine.function.FunctionDefinition;
import com.opengamma.engine.function.config.FunctionConfiguration;
import com.opengamma.engine.function.config.ParameterizedFunctionConfiguration;
import com.opengamma.engine.function.config.RepositoryConfiguration;
import com.opengamma.engine.function.config.RepositoryConfigurationSource;
import com.opengamma.engine.function.config.StaticFunctionConfiguration;
import com.opengamma.engine.value.ValueRequirementNames;
import com.opengamma.financial.aggregation.BottomPositionValues;
import com.opengamma.financial.aggregation.SortedPositionValues;
import com.opengamma.financial.aggregation.TopPositionValues;
import com.opengamma.financial.analytics.DummyLabelledMatrix2DPortfolioNodeFunction;
import com.opengamma.financial.analytics.DummyLabelledMatrix2DPositionFunction;
import com.opengamma.financial.analytics.DummyPortfolioNodeFunction;
import com.opengamma.financial.analytics.DummyPortfolioNodeMultipleCurrencyAmountFunction;
import com.opengamma.financial.analytics.FilteringSummingFunction;
import com.opengamma.financial.analytics.LastHistoricalValueFunction;
import com.opengamma.financial.analytics.PositionScalingFunction;
import com.opengamma.financial.analytics.PositionTradeScalingFunction;
import com.opengamma.financial.analytics.PositionWeightFromNAVFunction;
import com.opengamma.financial.analytics.SummingFunction;
import com.opengamma.financial.analytics.UnitPositionScalingFunction;
import com.opengamma.financial.analytics.UnitPositionTradeScalingFunction;
import com.opengamma.financial.analytics.equity.SecurityMarketPriceFunction;
import com.opengamma.financial.analytics.ircurve.DefaultYieldCurveMarketDataShiftFunction;
import com.opengamma.financial.analytics.ircurve.DefaultYieldCurveShiftFunction;
import com.opengamma.financial.analytics.ircurve.MarketInstrumentImpliedYieldCurveFunction;
import com.opengamma.financial.analytics.ircurve.YieldCurveMarketDataShiftFunction;
import com.opengamma.financial.analytics.ircurve.YieldCurveShiftFunction;
import com.opengamma.financial.analytics.model.bond.BondCleanPriceFromCurvesFunction;
import com.opengamma.financial.analytics.model.bond.BondCleanPriceFromYieldFunction;
import com.opengamma.financial.analytics.model.bond.BondCouponPaymentDiaryFunction;
import com.opengamma.financial.analytics.model.bond.BondDirtyPriceFromCurvesFunction;
import com.opengamma.financial.analytics.model.bond.BondDirtyPriceFromYieldFunction;
import com.opengamma.financial.analytics.model.bond.BondMacaulayDurationFromCurvesFunction;
import com.opengamma.financial.analytics.model.bond.BondMacaulayDurationFromYieldFunction;
import com.opengamma.financial.analytics.model.bond.BondMarketCleanPriceFunction;
import com.opengamma.financial.analytics.model.bond.BondMarketDirtyPriceFunction;
import com.opengamma.financial.analytics.model.bond.BondMarketYieldFunction;
import com.opengamma.financial.analytics.model.bond.BondModifiedDurationFromCurvesFunction;
import com.opengamma.financial.analytics.model.bond.BondModifiedDurationFromYieldFunction;
import com.opengamma.financial.analytics.model.bond.BondTenorFunction;
import com.opengamma.financial.analytics.model.bond.BondYieldFromCurvesFunction;
import com.opengamma.financial.analytics.model.bond.BondZSpreadFromCurvesFunction;
import com.opengamma.financial.analytics.model.bond.NelsonSiegelSvenssonBondCurveFunction;
import com.opengamma.financial.analytics.model.equity.futures.EquityFutureYieldCurveNodeSensitivityFunction;
import com.opengamma.financial.analytics.model.equity.futures.EquityFuturesFunction;
import com.opengamma.financial.analytics.model.equity.futures.EquityIndexDividendFutureYieldCurveNodeSensitivityFunction;
import com.opengamma.financial.analytics.model.equity.futures.EquityIndexDividendFuturesFunction;
import com.opengamma.financial.analytics.model.equity.portfoliotheory.CAPMBetaModelPortfolioNodeFunction;
import com.opengamma.financial.analytics.model.equity.portfoliotheory.CAPMBetaModelPositionFunction;
import com.opengamma.financial.analytics.model.equity.portfoliotheory.CAPMFromRegressionModelPortfolioNodeFunction;
import com.opengamma.financial.analytics.model.equity.portfoliotheory.CAPMFromRegressionModelPositionFunction;
import com.opengamma.financial.analytics.model.equity.portfoliotheory.JensenAlphaPortfolioNodeFunction;
import com.opengamma.financial.analytics.model.equity.portfoliotheory.JensenAlphaPositionFunction;
import com.opengamma.financial.analytics.model.equity.portfoliotheory.SharpeRatioPortfolioNodeFunction;
import com.opengamma.financial.analytics.model.equity.portfoliotheory.SharpeRatioPositionFunction;
import com.opengamma.financial.analytics.model.equity.portfoliotheory.StandardEquityModelFunction;
import com.opengamma.financial.analytics.model.equity.portfoliotheory.TotalRiskAlphaPortfolioNodeFunction;
import com.opengamma.financial.analytics.model.equity.portfoliotheory.TotalRiskAlphaPositionFunction;
import com.opengamma.financial.analytics.model.equity.portfoliotheory.TreynorRatioPortfolioNodeFunction;
import com.opengamma.financial.analytics.model.equity.portfoliotheory.TreynorRatioPositionFunction;
import com.opengamma.financial.analytics.model.equity.variance.EquityForwardFromSpotAndYieldCurveFunction;
import com.opengamma.financial.analytics.model.equity.variance.EquityVarianceSwapPresentValueFunction;
import com.opengamma.financial.analytics.model.equity.variance.EquityVarianceSwapVegaFunction;
import com.opengamma.financial.analytics.model.equity.variance.EquityVarianceSwapYieldCurveNodeSensitivityFunction;
import com.opengamma.financial.analytics.model.fixedincome.InterestRateInstrumentPV01Function;
import com.opengamma.financial.analytics.model.fixedincome.InterestRateInstrumentParRateFunction;
import com.opengamma.financial.analytics.model.fixedincome.InterestRateInstrumentParRateParallelCurveSensitivityFunction;
import com.opengamma.financial.analytics.model.fixedincome.InterestRateInstrumentPresentValueFunction;
import com.opengamma.financial.analytics.model.fixedincome.InterestRateInstrumentYieldCurveNodeSensitivitiesFunction;
import com.opengamma.financial.analytics.model.forex.ForexForwardCurrencyExposureFunction;
import com.opengamma.financial.analytics.model.forex.ForexForwardPresentValueCurveSensitivityFunction;
import com.opengamma.financial.analytics.model.forex.ForexForwardPresentValueFunction;
import com.opengamma.financial.analytics.model.forex.ForexForwardYieldCurveNodeSensitivitiesFunction;
import com.opengamma.financial.analytics.model.forex.ForexSingleBarrierOptionCurrencyExposureFunction;
import com.opengamma.financial.analytics.model.forex.ForexSingleBarrierOptionPresentValueCurveSensitivityFunction;
import com.opengamma.financial.analytics.model.forex.ForexSingleBarrierOptionPresentValueFunction;
import com.opengamma.financial.analytics.model.forex.ForexSingleBarrierOptionPresentValueVolatilitySensitivityFunction;
import com.opengamma.financial.analytics.model.forex.ForexSingleBarrierOptionVegaFunction;
import com.opengamma.financial.analytics.model.forex.ForexSingleBarrierOptionVegaQuoteFunction;
import com.opengamma.financial.analytics.model.forex.ForexSingleBarrierOptionYieldCurveNodeSensitivitiesFunction;
import com.opengamma.financial.analytics.model.forex.ForexVanillaOptionCurrencyExposureFunction;
import com.opengamma.financial.analytics.model.forex.ForexVanillaOptionPresentValueCurveSensitivityFunction;
import com.opengamma.financial.analytics.model.forex.ForexVanillaOptionPresentValueFunction;
import com.opengamma.financial.analytics.model.forex.ForexVanillaOptionPresentValueVolatilitySensitivityFunction;
import com.opengamma.financial.analytics.model.forex.ForexVanillaOptionVegaFunction;
import com.opengamma.financial.analytics.model.forex.ForexVanillaOptionVegaQuoteFunction;
import com.opengamma.financial.analytics.model.forex.ForexVanillaOptionYieldCurveNodeSensitivitiesFunction;
import com.opengamma.financial.analytics.model.future.BondFutureGrossBasisFromCurvesFunction;
import com.opengamma.financial.analytics.model.future.BondFutureNetBasisFromCurvesFunction;
import com.opengamma.financial.analytics.model.irfutureoption.InterestRateFutureOptionHestonPresentValueFunction;
import com.opengamma.financial.analytics.model.irfutureoption.InterestRateFutureOptionPresentValueFunction;
import com.opengamma.financial.analytics.model.irfutureoption.InterestRateFutureOptionSABRSensitivitiesFunction;
import com.opengamma.financial.analytics.model.irfutureoption.InterestRateFutureOptionVegaFunction;
import com.opengamma.financial.analytics.model.irfutureoption.InterestRateFutureOptionYieldCurveNodeSensitivitiesFunction;
import com.opengamma.financial.analytics.model.option.BlackScholesMertonModelFunction;
import com.opengamma.financial.analytics.model.option.BlackScholesModelCostOfCarryFunction;
import com.opengamma.financial.analytics.model.pnl.EquityPnLFunction;
import com.opengamma.financial.analytics.model.pnl.PortfolioExchangeTradedDailyPnLFunction;
import com.opengamma.financial.analytics.model.pnl.PortfolioExchangeTradedPnLFunction;
import com.opengamma.financial.analytics.model.pnl.PositionExchangeTradedDailyPnLFunction;
import com.opengamma.financial.analytics.model.pnl.PositionExchangeTradedPnLFunction;
import com.opengamma.financial.analytics.model.pnl.PositionValueGreekSensitivityPnLFunction;
import com.opengamma.financial.analytics.model.pnl.SecurityPriceSeriesFunction;
import com.opengamma.financial.analytics.model.pnl.TradeExchangeTradedDailyPnLFunction;
import com.opengamma.financial.analytics.model.pnl.TradeExchangeTradedPnLFunction;
import com.opengamma.financial.analytics.model.riskfactor.option.OptionGreekToValueGreekConverterFunction;
import com.opengamma.financial.analytics.model.sabrcube.SABRPresentValueCapFloorCMSSpreadFunction;
import com.opengamma.financial.analytics.model.sabrcube.SABRPresentValueCurveSensitivityCapFloorCMSSpreadFunction;
import com.opengamma.financial.analytics.model.sabrcube.SABRPresentValueCurveSensitivityFunction;
import com.opengamma.financial.analytics.model.sabrcube.SABRPresentValueFunction;
import com.opengamma.financial.analytics.model.sabrcube.SABRPresentValueSABRCapFloorCMSSpreadFunction;
import com.opengamma.financial.analytics.model.sabrcube.SABRPresentValueSABRFunction;
import com.opengamma.financial.analytics.model.sabrcube.SABRVegaCapFloorCMSSpreadFunction;
import com.opengamma.financial.analytics.model.sabrcube.SABRVegaFunction;
import com.opengamma.financial.analytics.model.sabrcube.SABRYieldCurveNodeSensitivitiesCapFloorCMSSpreadFunction;
import com.opengamma.financial.analytics.model.sabrcube.SABRYieldCurveNodeSensitivitiesFunction;
import com.opengamma.financial.analytics.model.var.OptionPortfolioParametricVaRCalculatorFunction;
import com.opengamma.financial.analytics.model.var.OptionPositionParametricVaRCalculatorFunction;
import com.opengamma.financial.analytics.model.var.PortfolioHistoricalVaRCalculatorFunction;
import com.opengamma.financial.analytics.model.var.PositionHistoricalVaRCalculatorFunction;
import com.opengamma.financial.analytics.timeseries.sampling.TimeSeriesSamplingFunctionFactory;
import com.opengamma.financial.analytics.volatility.cube.fitting.SABRNonLinearLeastSquaresSwaptionCubeFittingFunction;
import com.opengamma.financial.analytics.volatility.surface.DefaultVolatilitySurfaceShiftFunction;
import com.opengamma.financial.analytics.volatility.surface.VolatilitySurfaceShiftFunction;
import com.opengamma.financial.analytics.volatility.surface.fitting.BlackScholesMertonImpliedVolatilitySurfaceFunction;
import com.opengamma.financial.analytics.volatility.surface.fitting.HestonFourierIRFutureSurfaceFittingFunction;
import com.opengamma.financial.analytics.volatility.surface.fitting.SABRNonLinearLeastSquaresIRFutureSurfaceFittingFunction;
import com.opengamma.financial.currency.CurrencyMatrixConfigPopulator;
import com.opengamma.financial.currency.CurrencyMatrixSourcingFunction;
import com.opengamma.financial.currency.DefaultCurrencyInjectionFunction;
import com.opengamma.financial.currency.PortfolioNodeCurrencyConversionFunction;
import com.opengamma.financial.currency.PortfolioNodeDefaultCurrencyFunction;
import com.opengamma.financial.currency.PositionCurrencyConversionFunction;
import com.opengamma.financial.currency.PositionDefaultCurrencyFunction;
import com.opengamma.financial.currency.SecurityCurrencyConversionFunction;
import com.opengamma.financial.currency.SecurityDefaultCurrencyFunction;
import com.opengamma.financial.equity.future.pricing.EquityFuturePricerFactory;
import com.opengamma.financial.equity.variance.pricing.VarianceSwapStaticReplication;
import com.opengamma.financial.property.PortfolioNodeCalcConfigDefaultPropertyFunction;
import com.opengamma.financial.property.PositionCalcConfigDefaultPropertyFunction;
import com.opengamma.financial.property.PositionDefaultPropertyFunction;
import com.opengamma.financial.property.PrimitiveCalcConfigDefaultPropertyFunction;
import com.opengamma.financial.property.SecurityCalcConfigDefaultPropertyFunction;
import com.opengamma.financial.property.TradeCalcConfigDefaultPropertyFunction;
import com.opengamma.financial.property.TradeDefaultPropertyFunction;
import com.opengamma.financial.schedule.ScheduleCalculatorFactory;
import com.opengamma.financial.timeseries.returns.TimeSeriesReturnCalculatorFactory;
import com.opengamma.math.statistics.descriptive.StatisticsCalculatorFactory;
import com.opengamma.util.SingletonFactoryBean;
import com.opengamma.util.fudgemsg.OpenGammaFudgeContext;

/**
 * Constructs a standard function repository.
 * <p>
 * This should be replaced by something that loads the functions from the configuration database
 */
public class DemoStandardFunctionConfiguration extends SingletonFactoryBean<RepositoryConfigurationSource> {

  private static final boolean OUTPUT_REPO_CONFIGURATION = false;

  protected static <F extends FunctionDefinition> FunctionConfiguration functionConfiguration(final Class<F> clazz, String... args) {
    if (Modifier.isAbstract(clazz.getModifiers())) {
      throw new IllegalStateException("Attempting to register an abstract class - " + clazz);
    }
    if (args.length == 0) {
      return new StaticFunctionConfiguration(clazz.getName());
    } else {
      return new ParameterizedFunctionConfiguration(clazz.getName(), Arrays.asList(args));
    }
  }

  protected static void addScalingFunction(List<FunctionConfiguration> functionConfigs, String requirementName) {
    functionConfigs.add(functionConfiguration(PositionScalingFunction.class, requirementName));
    functionConfigs.add(functionConfiguration(PositionTradeScalingFunction.class, requirementName));
  }

  protected static void addUnitScalingFunction(List<FunctionConfiguration> functionConfigs, String requirementName) {
    functionConfigs.add(functionConfiguration(UnitPositionScalingFunction.class, requirementName));
    functionConfigs.add(functionConfiguration(UnitPositionTradeScalingFunction.class, requirementName));
  }

  protected static void addDummyFunction(List<FunctionConfiguration> functionConfigs, String requirementName) {
    functionConfigs.add(functionConfiguration(DummyPortfolioNodeFunction.class, requirementName, "0"));
  }

  protected static void addDummyMultipleCurrencyAmountFunction(List<FunctionConfiguration> functionConfigs, String requirementName) {
    functionConfigs.add(functionConfiguration(DummyPortfolioNodeMultipleCurrencyAmountFunction.class, requirementName));
  }

  protected static void addSummingFunction(List<FunctionConfiguration> functionConfigs, String requirementName) {
    functionConfigs.add(functionConfiguration(SummingFunction.class, requirementName));
  }

  protected static void addFilteredSummingFunction(List<FunctionConfiguration> functionConfigs, String requirementName) {
    functionConfigs.add(functionConfiguration(FilteringSummingFunction.class, requirementName));
  }

  protected static void addValueGreekAndSummingFunction(List<FunctionConfiguration> functionConfigs, String requirementName) {
    functionConfigs.add(functionConfiguration(OptionGreekToValueGreekConverterFunction.class, requirementName));
    addFilteredSummingFunction(functionConfigs, requirementName);
  }

  protected static void addCurrencyConversionFunctions(List<FunctionConfiguration> functionConfigs, String requirementName) {
    functionConfigs.add(functionConfiguration(PortfolioNodeCurrencyConversionFunction.class, requirementName));
    functionConfigs.add(functionConfiguration(PositionCurrencyConversionFunction.class, requirementName));
    functionConfigs.add(functionConfiguration(SecurityCurrencyConversionFunction.class, requirementName));
    functionConfigs.add(functionConfiguration(PortfolioNodeDefaultCurrencyFunction.Strict.class, requirementName));
    functionConfigs.add(functionConfiguration(PositionDefaultCurrencyFunction.Strict.class, requirementName));
    functionConfigs.add(functionConfiguration(SecurityDefaultCurrencyFunction.Strict.class, requirementName));
  }

  protected static void addCurrencyConversionFunctions(final List<FunctionConfiguration> functionConfigs) {
    addCurrencyConversionFunctions(functionConfigs, ValueRequirementNames.FAIR_VALUE);
    addCurrencyConversionFunctions(functionConfigs, ValueRequirementNames.PV01);
    addCurrencyConversionFunctions(functionConfigs, ValueRequirementNames.PRESENT_VALUE);
    addCurrencyConversionFunctions(functionConfigs, ValueRequirementNames.DAILY_PNL);
    //TODO PRESENT_VALUE_CURVE_SENSITIVITY
    addCurrencyConversionFunctions(functionConfigs, ValueRequirementNames.VALUE_DELTA);
    addCurrencyConversionFunctions(functionConfigs, ValueRequirementNames.VALUE_GAMMA);
    addCurrencyConversionFunctions(functionConfigs, ValueRequirementNames.VALUE_SPEED);
    functionConfigs.add(functionConfiguration(SecurityCurrencyConversionFunction.class, ValueRequirementNames.YIELD_CURVE_NODE_SENSITIVITIES));
    functionConfigs.add(functionConfiguration(PortfolioNodeDefaultCurrencyFunction.Permissive.class, ValueRequirementNames.YIELD_CURVE_NODE_SENSITIVITIES));
    functionConfigs.add(functionConfiguration(CurrencyMatrixSourcingFunction.class, CurrencyMatrixConfigPopulator.BLOOMBERG_LIVE_DATA));
    functionConfigs.add(functionConfiguration(CurrencyMatrixSourcingFunction.class, CurrencyMatrixConfigPopulator.SYNTHETIC_LIVE_DATA));
    functionConfigs.add(functionConfiguration(DefaultCurrencyInjectionFunction.class));
    // functionConfigs.add(functionConfiguration(CurrencyInversionFunction.class));
    // functionConfigs.add(functionConfiguration(CurrencyCrossRateFunction.class, "USD"));
    // functionConfigs.add(functionConfiguration(BloombergCurrencyRateFunction.class));
  }

  protected static void addLateAggregationFunctions(final List<FunctionConfiguration> functionConfigs) {
    functionConfigs.add(functionConfiguration(BottomPositionValues.class));
    functionConfigs.add(functionConfiguration(SortedPositionValues.class));
    functionConfigs.add(functionConfiguration(TopPositionValues.class));
  }

  protected static void addDataShiftingFunctions(final List<FunctionConfiguration> functionConfigs) {
    functionConfigs.add(functionConfiguration(VolatilitySurfaceShiftFunction.class));
    functionConfigs.add(functionConfiguration(DefaultVolatilitySurfaceShiftFunction.class));
    functionConfigs.add(functionConfiguration(YieldCurveShiftFunction.class));
    functionConfigs.add(functionConfiguration(DefaultYieldCurveShiftFunction.class));
    functionConfigs.add(functionConfiguration(YieldCurveMarketDataShiftFunction.class));
    functionConfigs.add(functionConfiguration(DefaultYieldCurveMarketDataShiftFunction.class));
  }

  protected static void addDefaultPropertyFunctions(final List<FunctionConfiguration> functionConfigs) {
    functionConfigs.add(functionConfiguration(PortfolioNodeCalcConfigDefaultPropertyFunction.Generic.class));
    functionConfigs.add(functionConfiguration(PortfolioNodeCalcConfigDefaultPropertyFunction.Specific.class));
    functionConfigs.add(functionConfiguration(PositionCalcConfigDefaultPropertyFunction.Generic.class));
    functionConfigs.add(functionConfiguration(PositionCalcConfigDefaultPropertyFunction.Specific.class));
    functionConfigs.add(functionConfiguration(PrimitiveCalcConfigDefaultPropertyFunction.Generic.class));
    functionConfigs.add(functionConfiguration(PrimitiveCalcConfigDefaultPropertyFunction.Specific.class));
    functionConfigs.add(functionConfiguration(SecurityCalcConfigDefaultPropertyFunction.Generic.class));
    functionConfigs.add(functionConfiguration(SecurityCalcConfigDefaultPropertyFunction.Specific.class));
    functionConfigs.add(functionConfiguration(TradeCalcConfigDefaultPropertyFunction.Generic.class));
    functionConfigs.add(functionConfiguration(TradeCalcConfigDefaultPropertyFunction.Specific.class));
    functionConfigs.add(functionConfiguration(PositionDefaultPropertyFunction.class));
    functionConfigs.add(functionConfiguration(TradeDefaultPropertyFunction.class));
  }

  protected static void addHistoricalDataFunctions(final List<FunctionConfiguration> functionConfigs, final String requirementName) {
    addDummyFunction(functionConfigs, requirementName);
    addUnitScalingFunction(functionConfigs, requirementName);
    functionConfigs.add(functionConfiguration(LastHistoricalValueFunction.class, requirementName));
  }

  protected static void addHistoricalDataFunctions(final List<FunctionConfiguration> functionConfigs) {
    addHistoricalDataFunctions(functionConfigs, ValueRequirementNames.DAILY_VOLUME);
    addHistoricalDataFunctions(functionConfigs, ValueRequirementNames.DAILY_MARKET_CAP);
    addHistoricalDataFunctions(functionConfigs, ValueRequirementNames.DAILY_APPLIED_BETA);
    addHistoricalDataFunctions(functionConfigs, ValueRequirementNames.DAILY_PRICE);
  }

  public static RepositoryConfiguration constructRepositoryConfiguration() {
    final List<FunctionConfiguration> functionConfigs = new ArrayList<FunctionConfiguration>();

    functionConfigs.add(functionConfiguration(SecurityMarketPriceFunction.class));
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.SECURITY_IMPLIED_VOLATLITY);

    // options
    functionConfigs.add(functionConfiguration(BlackScholesMertonModelFunction.class));
    functionConfigs.add(functionConfiguration(BlackScholesMertonImpliedVolatilitySurfaceFunction.class));
    functionConfigs.add(functionConfiguration(BlackScholesModelCostOfCarryFunction.class));

    // equity and portfolio
    functionConfigs.add(functionConfiguration(PositionExchangeTradedPnLFunction.class));
    functionConfigs.add(functionConfiguration(PortfolioExchangeTradedPnLFunction.class));
    functionConfigs.add(functionConfiguration(PortfolioExchangeTradedDailyPnLFunction.class));

    String returnCalculatorName = TimeSeriesReturnCalculatorFactory.SIMPLE_NET_STRICT;
    String startDate = "2008-09-22";
    // TODO if this is changed, need to have the ability to change the # of observations in a year in the portfolio analysis calculators (e.g. Sharpe ratio)
    String scheduleName = ScheduleCalculatorFactory.DAILY;
    String samplingCalculatorName = TimeSeriesSamplingFunctionFactory.PREVIOUS_AND_FIRST_VALUE_PADDING;
    String expectedMarketReturnCalculatorName = StatisticsCalculatorFactory.MEAN;
    String expectedAssetReturnCalculatorName = StatisticsCalculatorFactory.MEAN;
    String expectedRiskFreeReturnCalculatorName = StatisticsCalculatorFactory.MEAN;
    String assetStandardDeviationCalculatorName = StatisticsCalculatorFactory.SAMPLE_STANDARD_DEVIATION;
    String marketStandardDeviationCalculatorName = StatisticsCalculatorFactory.SAMPLE_STANDARD_DEVIATION;

    functionConfigs.add(functionConfiguration(TradeExchangeTradedPnLFunction.class, DEFAULT_CONFIG_NAME, LAST_PRICE, "COST_OF_CARRY"));
    functionConfigs.add(functionConfiguration(TradeExchangeTradedDailyPnLFunction.class, DEFAULT_CONFIG_NAME, LAST_PRICE, "COST_OF_CARRY"));
    functionConfigs.add(functionConfiguration(PositionExchangeTradedDailyPnLFunction.class, DEFAULT_CONFIG_NAME, LAST_PRICE, "COST_OF_CARRY"));
    functionConfigs.add(functionConfiguration(SecurityPriceSeriesFunction.class, DEFAULT_CONFIG_NAME, LAST_PRICE, startDate, scheduleName, samplingCalculatorName));
    functionConfigs.add(functionConfiguration(EquityPnLFunction.class, returnCalculatorName));
    functionConfigs.add(functionConfiguration(PositionHistoricalVaRCalculatorFunction.class, StatisticsCalculatorFactory.MEAN, StatisticsCalculatorFactory.SAMPLE_STANDARD_DEVIATION, "0.99"));
    functionConfigs.add(functionConfiguration(PortfolioHistoricalVaRCalculatorFunction.class, StatisticsCalculatorFactory.MEAN, StatisticsCalculatorFactory.SAMPLE_STANDARD_DEVIATION, "0.99"));
    functionConfigs.add(functionConfiguration(OptionPositionParametricVaRCalculatorFunction.class, DEFAULT_CONFIG_NAME, startDate, returnCalculatorName, scheduleName, samplingCalculatorName, "0.99",
        "1", ValueRequirementNames.VALUE_DELTA));
    functionConfigs.add(functionConfiguration(OptionPortfolioParametricVaRCalculatorFunction.class, DEFAULT_CONFIG_NAME, startDate, returnCalculatorName, scheduleName, samplingCalculatorName, "0.99",
        "1", ValueRequirementNames.VALUE_DELTA));
    functionConfigs.add(functionConfiguration(PositionValueGreekSensitivityPnLFunction.class, DEFAULT_CONFIG_NAME, startDate, returnCalculatorName, scheduleName, samplingCalculatorName,
        ValueRequirementNames.VALUE_DELTA));
    functionConfigs.add(functionConfiguration(CAPMBetaModelPositionFunction.class, returnCalculatorName, startDate));
    functionConfigs.add(functionConfiguration(CAPMBetaModelPortfolioNodeFunction.class, returnCalculatorName, startDate));
    functionConfigs.add(functionConfiguration(CAPMFromRegressionModelPositionFunction.class, startDate));
    functionConfigs.add(functionConfiguration(CAPMFromRegressionModelPortfolioNodeFunction.class, startDate));
    functionConfigs.add(functionConfiguration(SharpeRatioPositionFunction.class, returnCalculatorName, StatisticsCalculatorFactory.MEAN, assetStandardDeviationCalculatorName, startDate));
    functionConfigs.add(functionConfiguration(SharpeRatioPortfolioNodeFunction.class, returnCalculatorName, StatisticsCalculatorFactory.MEAN, assetStandardDeviationCalculatorName, startDate));
    functionConfigs.add(functionConfiguration(TreynorRatioPositionFunction.class, expectedAssetReturnCalculatorName, expectedRiskFreeReturnCalculatorName, startDate));
    functionConfigs.add(functionConfiguration(TreynorRatioPortfolioNodeFunction.class, expectedAssetReturnCalculatorName, expectedRiskFreeReturnCalculatorName, startDate));
    functionConfigs.add(functionConfiguration(JensenAlphaPositionFunction.class, returnCalculatorName, expectedAssetReturnCalculatorName, expectedRiskFreeReturnCalculatorName,
        expectedMarketReturnCalculatorName, startDate));
    functionConfigs.add(functionConfiguration(JensenAlphaPortfolioNodeFunction.class, returnCalculatorName, expectedAssetReturnCalculatorName, expectedRiskFreeReturnCalculatorName,
        expectedMarketReturnCalculatorName, startDate));
    functionConfigs.add(functionConfiguration(TotalRiskAlphaPositionFunction.class, returnCalculatorName, expectedAssetReturnCalculatorName, expectedRiskFreeReturnCalculatorName,
        expectedMarketReturnCalculatorName, assetStandardDeviationCalculatorName, marketStandardDeviationCalculatorName, startDate));
    functionConfigs.add(functionConfiguration(TotalRiskAlphaPortfolioNodeFunction.class, returnCalculatorName, expectedAssetReturnCalculatorName, expectedRiskFreeReturnCalculatorName,
        expectedMarketReturnCalculatorName, assetStandardDeviationCalculatorName, marketStandardDeviationCalculatorName, startDate));
    functionConfigs.add(functionConfiguration(PositionWeightFromNAVFunction.class, "56000000"));

    addFixedIncomeInstrumentCalculators(functionConfigs);
    // Something to return a LabelledMatrix2D
    functionConfigs.add(functionConfiguration(DummyLabelledMatrix2DPositionFunction.class));
    functionConfigs.add(functionConfiguration(DummyLabelledMatrix2DPortfolioNodeFunction.class));

    functionConfigs.add(functionConfiguration(StandardEquityModelFunction.class));
    addBondCalculators(functionConfigs);
    addBondFutureCalculators(functionConfigs);
    addSABRCalculators(functionConfigs);
    addForexVanillaOptionCalculators(functionConfigs);
    addForexSingleBarrierOptionCalculators(functionConfigs);
    addForexForwardCalculators(functionConfigs);
    addInterestRateFutureOptionCalculators(functionConfigs);
    addEquityDerivativesFunctions(functionConfigs);

    addDummyFunction(functionConfigs, ValueRequirementNames.PAR_RATE);
    addDummyFunction(functionConfigs, ValueRequirementNames.PAR_RATE_PARALLEL_CURVE_SHIFT);

    addScalingFunction(functionConfigs, ValueRequirementNames.FAIR_VALUE);

    addUnitScalingFunction(functionConfigs, ValueRequirementNames.DELTA);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.DELTA_BLEED);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.STRIKE_DELTA);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.DRIFTLESS_THETA);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.GAMMA);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.GAMMA_P);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.STRIKE_GAMMA);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.GAMMA_BLEED);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.GAMMA_P_BLEED);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.VEGA);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.VEGA_P);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.VARIANCE_VEGA);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.VEGA_BLEED);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.THETA);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.RHO);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.CARRY_RHO);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.ZETA);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.ZETA_BLEED);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.DZETA_DVOL);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.ELASTICITY);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.PHI);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.ZOMMA);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.ZOMMA_P);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.ULTIMA);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.VARIANCE_ULTIMA);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.SPEED);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.SPEED_P);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.VANNA);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.VARIANCE_VANNA);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.DVANNA_DVOL);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.VOMMA);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.VOMMA_P);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.VARIANCE_VOMMA);

    addUnitScalingFunction(functionConfigs, ValueRequirementNames.BOND_TENOR);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.MARKET_DIRTY_PRICE);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.MARKET_CLEAN_PRICE);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.MARKET_YTM);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.CLEAN_PRICE);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.DIRTY_PRICE);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.YTM);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.MODIFIED_DURATION);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.Z_SPREAD);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.CONVEXITY);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.MACAULAY_DURATION);

    addUnitScalingFunction(functionConfigs, ValueRequirementNames.GROSS_BASIS);
    addSummingFunction(functionConfigs, ValueRequirementNames.GROSS_BASIS);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.NET_BASIS);
    addSummingFunction(functionConfigs, ValueRequirementNames.NET_BASIS);

    addScalingFunction(functionConfigs, ValueRequirementNames.PV01);
    addScalingFunction(functionConfigs, ValueRequirementNames.PRESENT_VALUE);
    addScalingFunction(functionConfigs, ValueRequirementNames.PRESENT_VALUE_CURVE_SENSITIVITY);
    addScalingFunction(functionConfigs, ValueRequirementNames.PRESENT_VALUE_SABR_ALPHA_SENSITIVITY);
    addScalingFunction(functionConfigs, ValueRequirementNames.PRESENT_VALUE_SABR_NU_SENSITIVITY);
    addScalingFunction(functionConfigs, ValueRequirementNames.PRESENT_VALUE_SABR_RHO_SENSITIVITY);
    addScalingFunction(functionConfigs, ValueRequirementNames.YIELD_CURVE_NODE_SENSITIVITIES);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.PAR_RATE);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.PAR_RATE_PARALLEL_CURVE_SHIFT);

    addSummingFunction(functionConfigs, ValueRequirementNames.FAIR_VALUE);

    addDummyFunction(functionConfigs, ValueRequirementNames.DELTA);
    addDummyFunction(functionConfigs, ValueRequirementNames.DELTA_BLEED);
    addDummyFunction(functionConfigs, ValueRequirementNames.STRIKE_DELTA);
    addDummyFunction(functionConfigs, ValueRequirementNames.DRIFTLESS_THETA);
    addDummyFunction(functionConfigs, ValueRequirementNames.GAMMA);
    addDummyFunction(functionConfigs, ValueRequirementNames.GAMMA_P);
    addDummyFunction(functionConfigs, ValueRequirementNames.STRIKE_GAMMA);
    addDummyFunction(functionConfigs, ValueRequirementNames.GAMMA_BLEED);
    addDummyFunction(functionConfigs, ValueRequirementNames.GAMMA_P_BLEED);
    addDummyFunction(functionConfigs, ValueRequirementNames.VEGA);
    addDummyFunction(functionConfigs, ValueRequirementNames.VEGA_P);
    addDummyFunction(functionConfigs, ValueRequirementNames.VARIANCE_VEGA);
    addDummyFunction(functionConfigs, ValueRequirementNames.VEGA_BLEED);
    addDummyFunction(functionConfigs, ValueRequirementNames.THETA);
    addDummyFunction(functionConfigs, ValueRequirementNames.RHO);
    addDummyFunction(functionConfigs, ValueRequirementNames.CARRY_RHO);
    addDummyFunction(functionConfigs, ValueRequirementNames.ZETA);
    addDummyFunction(functionConfigs, ValueRequirementNames.ZETA_BLEED);
    addDummyFunction(functionConfigs, ValueRequirementNames.DZETA_DVOL);
    addDummyFunction(functionConfigs, ValueRequirementNames.ELASTICITY);
    addDummyFunction(functionConfigs, ValueRequirementNames.PHI);
    addDummyFunction(functionConfigs, ValueRequirementNames.ZOMMA);
    addDummyFunction(functionConfigs, ValueRequirementNames.ZOMMA_P);
    addDummyFunction(functionConfigs, ValueRequirementNames.ULTIMA);
    addDummyFunction(functionConfigs, ValueRequirementNames.VARIANCE_ULTIMA);
    addDummyFunction(functionConfigs, ValueRequirementNames.SPEED);
    addDummyFunction(functionConfigs, ValueRequirementNames.SPEED_P);
    addDummyFunction(functionConfigs, ValueRequirementNames.VANNA);
    addDummyFunction(functionConfigs, ValueRequirementNames.VARIANCE_VANNA);
    addDummyFunction(functionConfigs, ValueRequirementNames.DVANNA_DVOL);
    addDummyFunction(functionConfigs, ValueRequirementNames.VOMMA);
    addDummyFunction(functionConfigs, ValueRequirementNames.VOMMA_P);
    addDummyFunction(functionConfigs, ValueRequirementNames.VARIANCE_VOMMA);

    addDummyFunction(functionConfigs, ValueRequirementNames.BOND_TENOR);

    addFilteredSummingFunction(functionConfigs, ValueRequirementNames.PRESENT_VALUE);
    addFilteredSummingFunction(functionConfigs, ValueRequirementNames.PV01);
    addFilteredSummingFunction(functionConfigs, ValueRequirementNames.FX_CURRENCY_EXPOSURE);
    addFilteredSummingFunction(functionConfigs, ValueRequirementNames.FX_PRESENT_VALUE);
    addFilteredSummingFunction(functionConfigs, ValueRequirementNames.VEGA_MATRIX);
    addFilteredSummingFunction(functionConfigs, ValueRequirementNames.VEGA_QUOTE_MATRIX);
    addFilteredSummingFunction(functionConfigs, ValueRequirementNames.VEGA_QUOTE_CUBE);
    addSummingFunction(functionConfigs, ValueRequirementNames.PRESENT_VALUE_CURVE_SENSITIVITY);
    addSummingFunction(functionConfigs, ValueRequirementNames.PRICE_SERIES);
    addSummingFunction(functionConfigs, ValueRequirementNames.PNL_SERIES);
    addSummingFunction(functionConfigs, ValueRequirementNames.WEIGHT);

    addDummyFunction(functionConfigs, ValueRequirementNames.MARKET_CLEAN_PRICE);
    addDummyFunction(functionConfigs, ValueRequirementNames.MARKET_DIRTY_PRICE);
    addDummyFunction(functionConfigs, ValueRequirementNames.MARKET_YTM);
    addDummyFunction(functionConfigs, ValueRequirementNames.CLEAN_PRICE);
    addDummyFunction(functionConfigs, ValueRequirementNames.YTM);
    addDummyFunction(functionConfigs, ValueRequirementNames.DIRTY_PRICE);
    addDummyFunction(functionConfigs, ValueRequirementNames.MODIFIED_DURATION);
    addDummyFunction(functionConfigs, ValueRequirementNames.Z_SPREAD);
    addDummyFunction(functionConfigs, ValueRequirementNames.IMPLIED_REPO);
    addDummyFunction(functionConfigs, ValueRequirementNames.CONVEXITY);
    addDummyFunction(functionConfigs, ValueRequirementNames.MACAULAY_DURATION);
    addSummingFunction(functionConfigs, ValueRequirementNames.BOND_COUPON_PAYMENT_TIMES);
    addScalingFunction(functionConfigs, ValueRequirementNames.BOND_COUPON_PAYMENT_TIMES);

    addScalingFunction(functionConfigs, ValueRequirementNames.FX_PRESENT_VALUE);
    addScalingFunction(functionConfigs, ValueRequirementNames.FX_CURRENCY_EXPOSURE);
    addDummyFunction(functionConfigs, ValueRequirementNames.FX_CURVE_SENSITIVITIES);
    addUnitScalingFunction(functionConfigs, ValueRequirementNames.FX_CURVE_SENSITIVITIES);

    addScalingFunction(functionConfigs, ValueRequirementNames.VEGA_MATRIX);
    addSummingFunction(functionConfigs, ValueRequirementNames.VEGA_MATRIX);
    addScalingFunction(functionConfigs, ValueRequirementNames.VEGA_QUOTE_MATRIX);
    addSummingFunction(functionConfigs, ValueRequirementNames.VEGA_QUOTE_MATRIX);
    addScalingFunction(functionConfigs, ValueRequirementNames.VEGA_QUOTE_CUBE);
    addSummingFunction(functionConfigs, ValueRequirementNames.VEGA_QUOTE_CUBE);
    addScalingFunction(functionConfigs, ValueRequirementNames.FX_VOLATILITY_SENSITIVITIES);
    addSummingFunction(functionConfigs, ValueRequirementNames.FX_VOLATILITY_SENSITIVITIES);

    addScalingFunction(functionConfigs, ValueRequirementNames.VALUE_DELTA);
    addSummingFunction(functionConfigs, ValueRequirementNames.VALUE_DELTA);
    addScalingFunction(functionConfigs, ValueRequirementNames.VALUE_RHO);
    addSummingFunction(functionConfigs, ValueRequirementNames.VALUE_RHO);

    addUnitScalingFunction(functionConfigs, ValueRequirementNames.FORWARD);
    addSummingFunction(functionConfigs, ValueRequirementNames.FORWARD);
    addValueGreekAndSummingFunction(functionConfigs, ValueRequirementNames.VALUE_DELTA);
    addValueGreekAndSummingFunction(functionConfigs, ValueRequirementNames.VALUE_GAMMA);
    addValueGreekAndSummingFunction(functionConfigs, ValueRequirementNames.VALUE_SPEED);

    addCurrencyConversionFunctions(functionConfigs);
    addLateAggregationFunctions(functionConfigs);
    addDataShiftingFunctions(functionConfigs);
    addDefaultPropertyFunctions(functionConfigs);
    addHistoricalDataFunctions(functionConfigs);

    RepositoryConfiguration repoConfig = new RepositoryConfiguration(functionConfigs);

    if (OUTPUT_REPO_CONFIGURATION) {
      FudgeMsg msg = OpenGammaFudgeContext.getInstance().toFudgeMsg(repoConfig).getMessage();
      FudgeMsgFormatter.outputToSystemOut(msg);
      try {
        FudgeXMLSettings xmlSettings = new FudgeXMLSettings();
        xmlSettings.setEnvelopeElementName(null);
        FudgeMsgWriter msgWriter = new FudgeMsgWriter(new FudgeXMLStreamWriter(FudgeContext.GLOBAL_DEFAULT, new OutputStreamWriter(System.out), xmlSettings));
        msgWriter.setDefaultMessageProcessingDirectives(0);
        msgWriter.setDefaultMessageVersion(0);
        msgWriter.setDefaultTaxonomyId(0);
        msgWriter.writeMessage(msg);
        msgWriter.flush();
      } catch (Exception e) {
        // Just swallow it.
      }
    }
    return repoConfig;
  }

  private static void addEquityDerivativesFunctions(List<FunctionConfiguration> functionConfigs) {
    functionConfigs.add(new ParameterizedFunctionConfiguration(EquityFuturesFunction.class.getName(), 
        Arrays.asList(ValueRequirementNames.PRESENT_VALUE, EquityFuturePricerFactory.MARK_TO_MARKET, "FUNDING")));
    //functionConfigs.add(new ParameterizedFunctionConfiguration(EquityFuturesFunction.class.getName(), Arrays.asList(ValueRequirementNames.PRESENT_VALUE, EquityFuturePricerFactory.COST_OF_CARRY)));
    functionConfigs.add(new ParameterizedFunctionConfiguration(EquityFuturesFunction.class.getName(), 
        Arrays.asList(ValueRequirementNames.PRESENT_VALUE, EquityFuturePricerFactory.DIVIDEND_YIELD, "FUNDING")));
    functionConfigs.add(new ParameterizedFunctionConfiguration(EquityIndexDividendFuturesFunction.class.getName(), 
        Arrays.asList(ValueRequirementNames.PRESENT_VALUE, EquityFuturePricerFactory.MARK_TO_MARKET, "FUNDING")));
    //functionConfigs.add(new ParameterizedFunctionConfiguration(EquityFuturesFunction.class.getName(), Arrays.asList(ValueRequirementNames.PRESENT_VALUE, EquityFuturePricerFactory.COST_OF_CARRY)));
    functionConfigs.add(new ParameterizedFunctionConfiguration(EquityFuturesFunction.class.getName(), 
        Arrays.asList(ValueRequirementNames.PRESENT_VALUE, EquityFuturePricerFactory.DIVIDEND_YIELD, "FUNDING")));
    functionConfigs.add(new ParameterizedFunctionConfiguration(EquityFuturesFunction.class.getName(), 
        Arrays.asList(ValueRequirementNames.PV01, EquityFuturePricerFactory.MARK_TO_MARKET, "FUNDING")));
    //functionConfigs.add(new ParameterizedFunctionConfiguration(EquityFuturesFunction.class.getName(), Arrays.asList(ValueRequirementNames.PV01, EquityFuturePricerFactory.COST_OF_CARRY)));
    functionConfigs.add(new ParameterizedFunctionConfiguration(EquityFuturesFunction.class.getName(), 
        Arrays.asList(ValueRequirementNames.PV01, EquityFuturePricerFactory.DIVIDEND_YIELD, "FUNDING")));
    functionConfigs.add(new ParameterizedFunctionConfiguration(EquityFuturesFunction.class.getName(), 
        Arrays.asList(ValueRequirementNames.VALUE_RHO, EquityFuturePricerFactory.MARK_TO_MARKET, "FUNDING")));
    //functionConfigs.add(new ParameterizedFunctionConfiguration(EquityFuturesFunction.class.getName(), Arrays.asList(ValueRequirementNames.VALUE_RHO, EquityFuturePricerFactory.COST_OF_CARRY)));
    functionConfigs.add(new ParameterizedFunctionConfiguration(EquityFuturesFunction.class.getName(), 
        Arrays.asList(ValueRequirementNames.VALUE_RHO, EquityFuturePricerFactory.DIVIDEND_YIELD, "FUNDING")));
    functionConfigs.add(new ParameterizedFunctionConfiguration(EquityFuturesFunction.class.getName(), 
        Arrays.asList(ValueRequirementNames.VALUE_DELTA, EquityFuturePricerFactory.MARK_TO_MARKET, "FUNDING")));
    //functionConfigs.add(new ParameterizedFunctionConfiguration(EquityFuturesFunction.class.getName(), Arrays.asList(ValueRequirementNames.VALUE_DELTA, EquityFuturePricerFactory.COST_OF_CARRY)));
    functionConfigs.add(new ParameterizedFunctionConfiguration(EquityFuturesFunction.class.getName(), 
        Arrays.asList(ValueRequirementNames.VALUE_DELTA, EquityFuturePricerFactory.DIVIDEND_YIELD, "FUNDING")));
    functionConfigs.add(functionConfiguration(EquityFutureYieldCurveNodeSensitivityFunction.class, "FUNDING"));
    functionConfigs.add(functionConfiguration(EquityIndexDividendFutureYieldCurveNodeSensitivityFunction.class, "FUNDING"));
    functionConfigs.add(functionConfiguration(EquityForwardFromSpotAndYieldCurveFunction.class, "FUNDING"));
    functionConfigs.add(functionConfiguration(EquityVarianceSwapPresentValueFunction.class, "FUNDING", "DEFAULT", EquityForwardFromSpotAndYieldCurveFunction.FORWARD_FROM_SPOT_AND_YIELD_CURVE, 
        VarianceSwapStaticReplication.StrikeParameterization.STRIKE.toString()));
    functionConfigs.add(functionConfiguration(EquityVarianceSwapYieldCurveNodeSensitivityFunction.class, "FUNDING", "DEFAULT", 
        EquityForwardFromSpotAndYieldCurveFunction.FORWARD_FROM_SPOT_AND_YIELD_CURVE, VarianceSwapStaticReplication.StrikeParameterization.STRIKE.toString()));
    functionConfigs.add(functionConfiguration(EquityVarianceSwapVegaFunction.class, "FUNDING", "DEFAULT", 
        EquityForwardFromSpotAndYieldCurveFunction.FORWARD_FROM_SPOT_AND_YIELD_CURVE, VarianceSwapStaticReplication.StrikeParameterization.STRIKE.toString()));
  }

  private static void addBondFutureCalculators(List<FunctionConfiguration> functionConfigs) {
    functionConfigs.add(functionConfiguration(BondFutureGrossBasisFromCurvesFunction.class, "USD", "FUNDING", "FUNDING"));
    functionConfigs.add(functionConfiguration(BondFutureNetBasisFromCurvesFunction.class, "USD", "FUNDING", "FUNDING"));
  }

  private static void addBondCalculators(List<FunctionConfiguration> functionConfigs) {
    functionConfigs.add(functionConfiguration(BondCouponPaymentDiaryFunction.class));
    functionConfigs.add(functionConfiguration(BondTenorFunction.class));
    functionConfigs.add(functionConfiguration(BondMarketCleanPriceFunction.class));
    functionConfigs.add(functionConfiguration(BondMarketDirtyPriceFunction.class));
    functionConfigs.add(functionConfiguration(BondMarketYieldFunction.class));
    functionConfigs.add(functionConfiguration(BondYieldFromCurvesFunction.class, "USD", "FUNDING", "FUNDING"));
    functionConfigs.add(functionConfiguration(BondCleanPriceFromCurvesFunction.class, "USD", "FUNDING", "FUNDING"));
    functionConfigs.add(functionConfiguration(BondDirtyPriceFromCurvesFunction.class, "USD", "FUNDING", "FUNDING"));
    functionConfigs.add(functionConfiguration(BondMacaulayDurationFromCurvesFunction.class, "USD", "FUNDING", "FUNDING"));
    functionConfigs.add(functionConfiguration(BondModifiedDurationFromCurvesFunction.class, "USD", "FUNDING", "FUNDING"));
    functionConfigs.add(functionConfiguration(BondCleanPriceFromYieldFunction.class, "USD", "FUNDING", "FUNDING"));
    functionConfigs.add(functionConfiguration(BondDirtyPriceFromYieldFunction.class, "USD", "FUNDING", "FUNDING"));
    functionConfigs.add(functionConfiguration(BondMacaulayDurationFromYieldFunction.class, "USD", "FUNDING", "FUNDING"));
    functionConfigs.add(functionConfiguration(BondModifiedDurationFromYieldFunction.class, "USD", "FUNDING", "FUNDING"));
    functionConfigs.add(functionConfiguration(BondZSpreadFromCurvesFunction.class, "USD", "FUNDING", "FUNDING"));
    functionConfigs.add(functionConfiguration(NelsonSiegelSvenssonBondCurveFunction.class, "USD"));
  }

  private static void addForexSingleBarrierOptionCalculators(List<FunctionConfiguration> functionConfigs) {
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionPresentValueFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionPresentValueFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionPresentValueFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionPresentValueFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionCurrencyExposureFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionCurrencyExposureFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionCurrencyExposureFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionCurrencyExposureFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionPresentValueCurveSensitivityFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionPresentValueCurveSensitivityFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionPresentValueCurveSensitivityFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionPresentValueCurveSensitivityFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionPresentValueVolatilitySensitivityFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionPresentValueVolatilitySensitivityFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionPresentValueVolatilitySensitivityFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionPresentValueVolatilitySensitivityFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionYieldCurveNodeSensitivitiesFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionYieldCurveNodeSensitivitiesFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionYieldCurveNodeSensitivitiesFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionYieldCurveNodeSensitivitiesFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionVegaFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionVegaFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionVegaFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionVegaFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionVegaQuoteFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionVegaQuoteFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionVegaQuoteFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexSingleBarrierOptionVegaQuoteFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_6M", "DEFAULT"));
  }

  private static void addForexVanillaOptionCalculators(List<FunctionConfiguration> functionConfigs) {
    functionConfigs.add(functionConfiguration(ForexVanillaOptionPresentValueFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionPresentValueFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionPresentValueFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionPresentValueFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionCurrencyExposureFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionCurrencyExposureFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionCurrencyExposureFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionCurrencyExposureFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionPresentValueCurveSensitivityFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionPresentValueCurveSensitivityFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionPresentValueCurveSensitivityFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionPresentValueCurveSensitivityFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionPresentValueVolatilitySensitivityFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionPresentValueVolatilitySensitivityFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionPresentValueVolatilitySensitivityFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionPresentValueVolatilitySensitivityFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionYieldCurveNodeSensitivitiesFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionYieldCurveNodeSensitivitiesFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionYieldCurveNodeSensitivitiesFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionYieldCurveNodeSensitivitiesFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionVegaFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionVegaFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionVegaFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionVegaFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionVegaQuoteFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionVegaQuoteFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_6M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionVegaQuoteFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_3M", "DEFAULT"));
    functionConfigs.add(functionConfiguration(ForexVanillaOptionVegaQuoteFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_6M", "DEFAULT"));
  }

  private static void addForexForwardCalculators(List<FunctionConfiguration> functionConfigs) {
    functionConfigs.add(functionConfiguration(ForexForwardPresentValueFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_3M"));
    functionConfigs.add(functionConfiguration(ForexForwardPresentValueFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_6M"));
    functionConfigs.add(functionConfiguration(ForexForwardPresentValueFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_3M"));
    functionConfigs.add(functionConfiguration(ForexForwardPresentValueFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_6M"));
    functionConfigs.add(functionConfiguration(ForexForwardCurrencyExposureFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_3M"));
    functionConfigs.add(functionConfiguration(ForexForwardCurrencyExposureFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_6M"));
    functionConfigs.add(functionConfiguration(ForexForwardCurrencyExposureFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_3M"));
    functionConfigs.add(functionConfiguration(ForexForwardCurrencyExposureFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_6M"));
    functionConfigs.add(functionConfiguration(ForexForwardPresentValueCurveSensitivityFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_3M"));
    functionConfigs.add(functionConfiguration(ForexForwardPresentValueCurveSensitivityFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_6M"));
    functionConfigs.add(functionConfiguration(ForexForwardPresentValueCurveSensitivityFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_3M"));
    functionConfigs.add(functionConfiguration(ForexForwardPresentValueCurveSensitivityFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_6M"));
    functionConfigs.add(functionConfiguration(ForexForwardYieldCurveNodeSensitivitiesFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_3M"));
    functionConfigs.add(functionConfiguration(ForexForwardYieldCurveNodeSensitivitiesFunction.class, "FUNDING", "FORWARD_3M", "FUNDING", "FORWARD_6M"));
    functionConfigs.add(functionConfiguration(ForexForwardYieldCurveNodeSensitivitiesFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_3M"));
    functionConfigs.add(functionConfiguration(ForexForwardYieldCurveNodeSensitivitiesFunction.class, "FUNDING", "FORWARD_6M", "FUNDING", "FORWARD_6M"));
  }

  private static void addInterestRateFutureOptionCalculators(List<FunctionConfiguration> functionConfigs) {
    functionConfigs.add(functionConfiguration(InterestRateFutureOptionPresentValueFunction.class, "FORWARD_3M", "FUNDING", "DEFAULT"));
    functionConfigs.add(functionConfiguration(InterestRateFutureOptionPresentValueFunction.class, "FORWARD_6M", "FUNDING", "DEFAULT"));
    functionConfigs.add(functionConfiguration(InterestRateFutureOptionSABRSensitivitiesFunction.class, "FORWARD_3M", "FUNDING", "DEFAULT"));
    functionConfigs.add(functionConfiguration(InterestRateFutureOptionSABRSensitivitiesFunction.class, "FORWARD_6M", "FUNDING", "DEFAULT"));
    functionConfigs.add(functionConfiguration(InterestRateFutureOptionVegaFunction.class, "FORWARD_3M", "FUNDING", "DEFAULT"));
    functionConfigs.add(functionConfiguration(InterestRateFutureOptionVegaFunction.class, "FORWARD_6M", "FUNDING", "DEFAULT"));
    functionConfigs.add(functionConfiguration(InterestRateFutureOptionSABRSensitivitiesFunction.class, "FORWARD_3M", "FUNDING", "DEFAULT"));
    functionConfigs.add(functionConfiguration(InterestRateFutureOptionSABRSensitivitiesFunction.class, "FORWARD_6M", "FUNDING", "DEFAULT"));
    functionConfigs.add(functionConfiguration(InterestRateFutureOptionYieldCurveNodeSensitivitiesFunction.class, "FORWARD_3M", "FUNDING", "DEFAULT"));
    functionConfigs.add(functionConfiguration(InterestRateFutureOptionYieldCurveNodeSensitivitiesFunction.class, "FORWARD_6M", "FUNDING", "DEFAULT"));
    functionConfigs.add(functionConfiguration(SABRNonLinearLeastSquaresIRFutureSurfaceFittingFunction.class, "USD", "DEFAULT"));
    functionConfigs.add(functionConfiguration(HestonFourierIRFutureSurfaceFittingFunction.class, "USD", "DEFAULT"));
    functionConfigs.add(functionConfiguration(InterestRateFutureOptionHestonPresentValueFunction.class, "FORWARD_3M", "FUNDING", "DEFAULT"));
  }

  private static void addSABRCalculators(List<FunctionConfiguration> functionConfigs) {
    functionConfigs.add(new ParameterizedFunctionConfiguration(SABRNonLinearLeastSquaresSwaptionCubeFittingFunction.class.getName(), Arrays.asList("USD", "BLOOMBERG")));
    functionConfigs.add(new ParameterizedFunctionConfiguration(SABRPresentValueSABRCapFloorCMSSpreadFunction.class.getName(), Arrays.asList("USD", "BLOOMBERG", "FORWARD_3M", "FUNDING")));
    functionConfigs.add(new ParameterizedFunctionConfiguration(SABRPresentValueSABRFunction.class.getName(), Arrays.asList("USD", "BLOOMBERG", "FORWARD_3M", "FUNDING")));
    functionConfigs.add(new ParameterizedFunctionConfiguration(SABRPresentValueCapFloorCMSSpreadFunction.class.getName(), Arrays.asList("USD", "BLOOMBERG", "FORWARD_3M", "FUNDING")));
    functionConfigs.add(new ParameterizedFunctionConfiguration(SABRPresentValueFunction.class.getName(), Arrays.asList("USD", "BLOOMBERG", "false", "FORWARD_3M", "FUNDING")));
    functionConfigs.add(new ParameterizedFunctionConfiguration(SABRPresentValueCurveSensitivityFunction.class.getName(), Arrays.asList("USD", "BLOOMBERG", "false", "FORWARD_3M", "FUNDING")));
    functionConfigs.add(new ParameterizedFunctionConfiguration(SABRPresentValueCurveSensitivityCapFloorCMSSpreadFunction.class.getName(), Arrays.asList("USD", "BLOOMBERG", "FORWARD_3M", "FUNDING")));
    functionConfigs.add(new ParameterizedFunctionConfiguration(SABRYieldCurveNodeSensitivitiesFunction.class.getName(), Arrays.asList("USD", "BLOOMBERG", "false", "FORWARD_3M", "FUNDING")));
    functionConfigs.add(new ParameterizedFunctionConfiguration(SABRYieldCurveNodeSensitivitiesCapFloorCMSSpreadFunction.class.getName(), Arrays.asList("USD", "BLOOMBERG", "FORWARD_3M", "FUNDING")));
    functionConfigs.add(new ParameterizedFunctionConfiguration(SABRVegaCapFloorCMSSpreadFunction.class.getName(), Arrays.asList("USD", "BLOOMBERG", "FORWARD_3M", "FUNDING")));
    functionConfigs.add(new ParameterizedFunctionConfiguration(SABRVegaFunction.class.getName(), Arrays.asList("USD", "BLOOMBERG", "false", "FORWARD_3M", "FUNDING")));
    functionConfigs.add(new ParameterizedFunctionConfiguration(FilteringSummingFunction.class.getName(), Arrays.asList(ValueRequirementNames.PRESENT_VALUE_SABR_ALPHA_SENSITIVITY)));
    functionConfigs.add(new ParameterizedFunctionConfiguration(FilteringSummingFunction.class.getName(), Arrays.asList(ValueRequirementNames.PRESENT_VALUE_SABR_NU_SENSITIVITY)));
    functionConfigs.add(new ParameterizedFunctionConfiguration(FilteringSummingFunction.class.getName(), Arrays.asList(ValueRequirementNames.PRESENT_VALUE_SABR_RHO_SENSITIVITY)));
  }

  private static void addFixedIncomeInstrumentCalculators(List<FunctionConfiguration> functionConfigs) {
    //forward/funding
    functionConfigs.add(functionConfiguration(InterestRateInstrumentParRateFunction.class, "FORWARD_3M", "FUNDING"));
    functionConfigs.add(functionConfiguration(InterestRateInstrumentPresentValueFunction.class, "FORWARD_3M", "FUNDING"));
    functionConfigs.add(functionConfiguration(InterestRateInstrumentParRateParallelCurveSensitivityFunction.class, "FORWARD_3M", "FUNDING"));
    functionConfigs.add(functionConfiguration(InterestRateInstrumentPV01Function.class, "FORWARD_3M", "FUNDING"));
    functionConfigs.add(functionConfiguration(InterestRateInstrumentYieldCurveNodeSensitivitiesFunction.class, "FORWARD_3M", "FUNDING", MarketInstrumentImpliedYieldCurveFunction.PAR_RATE_STRING));
    functionConfigs
        .add(functionConfiguration(InterestRateInstrumentYieldCurveNodeSensitivitiesFunction.class, "FORWARD_3M", "FUNDING", MarketInstrumentImpliedYieldCurveFunction.PRESENT_VALUE_STRING));
    functionConfigs.add(functionConfiguration(InterestRateInstrumentParRateFunction.class, "FORWARD_6M", "FUNDING"));
    functionConfigs.add(functionConfiguration(InterestRateInstrumentPresentValueFunction.class, "FORWARD_6M", "FUNDING"));
    functionConfigs.add(functionConfiguration(InterestRateInstrumentParRateParallelCurveSensitivityFunction.class, "FORWARD_6M", "FUNDING"));
    functionConfigs.add(functionConfiguration(InterestRateInstrumentPV01Function.class, "FORWARD_6M", "FUNDING"));
    functionConfigs.add(functionConfiguration(InterestRateInstrumentYieldCurveNodeSensitivitiesFunction.class, "FORWARD_6M", "FUNDING", MarketInstrumentImpliedYieldCurveFunction.PAR_RATE_STRING));
    functionConfigs
        .add(functionConfiguration(InterestRateInstrumentYieldCurveNodeSensitivitiesFunction.class, "FORWARD_6M", "FUNDING", MarketInstrumentImpliedYieldCurveFunction.PRESENT_VALUE_STRING));
    addFilteredSummingFunction(functionConfigs, ValueRequirementNames.YIELD_CURVE_NODE_SENSITIVITIES);

    // Secondary
    functionConfigs.add(functionConfiguration(InterestRateInstrumentPresentValueFunction.class, "SECONDARY", "SECONDARY"));
    functionConfigs.add(functionConfiguration(
        InterestRateInstrumentYieldCurveNodeSensitivitiesFunction.class, "SECONDARY", "SECONDARY", MarketInstrumentImpliedYieldCurveFunction.PAR_RATE_STRING));
  }

  public static RepositoryConfigurationSource constructRepositoryConfigurationSource() {
    return new RepositoryConfigurationSource() {
      private final RepositoryConfiguration _config = constructRepositoryConfiguration();

      @Override
      public RepositoryConfiguration getRepositoryConfiguration() {
        return _config;
      }
    };
  }

  @Override
  protected RepositoryConfigurationSource createObject() {
    return constructRepositoryConfigurationSource();
  }

}
