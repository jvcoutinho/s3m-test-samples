/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.financial.analytics.ircurve;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import javax.time.calendar.LocalDate;
import javax.time.calendar.LocalTime;
import javax.time.calendar.Period;
import javax.time.calendar.TimeZone;
import javax.time.calendar.ZonedDateTime;

import com.opengamma.OpenGammaRuntimeException;
import com.opengamma.core.region.Region;
import com.opengamma.core.region.RegionSource;
import com.opengamma.core.security.Security;
import com.opengamma.core.security.SecuritySource;
import com.opengamma.core.security.SecurityUtils;
import com.opengamma.financial.convention.ConventionBundle;
import com.opengamma.financial.convention.ConventionBundleSource;
import com.opengamma.financial.convention.DefaultConventionBundleSource;
import com.opengamma.financial.convention.InMemoryConventionBundleMaster;
import com.opengamma.financial.security.cash.CashSecurity;
import com.opengamma.financial.security.fra.FRASecurity;
import com.opengamma.financial.security.future.FutureSecurity;
import com.opengamma.financial.security.swap.FixedInterestRateLeg;
import com.opengamma.financial.security.swap.FloatingInterestRateLeg;
import com.opengamma.financial.security.swap.InterestRateNotional;
import com.opengamma.financial.security.swap.SwapSecurity;
import com.opengamma.id.Identifier;
import com.opengamma.id.IdentifierBundle;
import com.opengamma.util.time.DateUtil;
import com.opengamma.util.time.Tenor;

/**
 * Converts specifications into fully resolved security definitions 
 */
public class FixedIncomeStripIdentifierAndMaturityBuilder {
  private static final LocalTime CASH_EXPIRY_TIME = LocalTime.of(11, 00);
  private final RegionSource _regionSource;
  private final ConventionBundleSource _conventionBundleSource;
  private final SecuritySource _secSource;

  public FixedIncomeStripIdentifierAndMaturityBuilder(final RegionSource regionSource,
      final ConventionBundleSource conventionBundleSource, final SecuritySource secSource) {
    _regionSource = regionSource;
    _conventionBundleSource = conventionBundleSource;
    _secSource = secSource;
  }

  public InterpolatedYieldCurveSpecificationWithSecurities resolveToSecurity(
      final InterpolatedYieldCurveSpecification curveSpecification, final Map<Identifier, Double> marketValues) {
    //Currency currency = curveSpecification.getCurrency();
    final LocalDate curveDate = curveSpecification.getCurveDate();
    final Collection<FixedIncomeStripWithSecurity> securityStrips = new ArrayList<FixedIncomeStripWithSecurity>();
    for (final FixedIncomeStripWithIdentifier strip : curveSpecification.getStrips()) {
      Security security;
      ZonedDateTime maturity;
      switch (strip.getInstrumentType()) {
        case CASH:
          final CashSecurity cashSecurity = getCash(curveSpecification, strip, marketValues);
          if (cashSecurity == null) {
            throw new OpenGammaRuntimeException("Could not resolve cash curve instrument " + strip.getSecurity()
                + " from strip " + strip + " in " + curveSpecification);
          }
          final Region region = _regionSource.getHighestLevelRegion(cashSecurity.getRegion());
          TimeZone timeZone = region.getTimeZone();
          timeZone = ensureZone(timeZone);
          maturity = curveDate.plus(strip.getMaturity().getPeriod()).atTime(CASH_EXPIRY_TIME).atZone(timeZone);
          security = cashSecurity;
          break;
        case FRA:
          final FRASecurity fraSecurity = getFRA(curveSpecification, strip, marketValues);
          if (fraSecurity == null) {
            throw new OpenGammaRuntimeException("Could not resolve FRA curve instrument " + strip.getSecurity()
                + " from strip " + strip + " in " + curveSpecification);
          }
          maturity = fraSecurity.getEndDate();
          security = fraSecurity;
          break;
        case FUTURE:
          // TODO: jim 17-Aug-2010 -- we need to sort out the zoned date time related to the expiry.
          final FutureSecurity futureSecurity = getFuture(curveSpecification, strip);
          if (futureSecurity == null) {
            throw new OpenGammaRuntimeException("Could not resolve future curve instrument " + strip.getSecurity()
                + " from strip " + strip + " in " + curveSpecification);
          }
          maturity = futureSecurity.getExpiry().getExpiry();
          security = futureSecurity;
          break;
        case LIBOR:
          final CashSecurity rateSecurity = getCash(curveSpecification, strip, marketValues);
          if (rateSecurity == null) {
            throw new OpenGammaRuntimeException("Could not resolve future curve instrument " + strip.getSecurity()
                + " from strip " + strip + " in " + curveSpecification);
          }
          final Region region2 = _regionSource.getHighestLevelRegion(rateSecurity.getRegion());
          TimeZone timeZone2 = region2.getTimeZone();
          timeZone2 = ensureZone(timeZone2);
          maturity = curveDate.plus(strip.getMaturity().getPeriod()).atTime(CASH_EXPIRY_TIME).atZone(timeZone2);
          security = rateSecurity;
          break;
        case SWAP:
          final SwapSecurity swapSecurity = getSwap(curveSpecification, strip, marketValues);
          if (swapSecurity == null) {
            throw new OpenGammaRuntimeException("Could not resolve swap curve instrument " + strip.getSecurity()
                + " from strip " + strip + " in " + curveSpecification);
          }
          maturity = swapSecurity.getMaturityDate();
          security = swapSecurity;
          break;
        case TENOR_SWAP:
          final SwapSecurity tenorSwapSecurity = getTenorSwap(curveSpecification, strip, marketValues);
          if (tenorSwapSecurity == null) {
            throw new OpenGammaRuntimeException("Could not resolve swap curve instrument " + strip.getSecurity()
                + " from strip " + strip + " in " + curveSpecification);
          }
          maturity = tenorSwapSecurity.getMaturityDate();
          security = tenorSwapSecurity;
          break;
        default:
          throw new OpenGammaRuntimeException("Unhandled type of instrument in curve definition "
              + strip.getInstrumentType());
      }
      final Tenor resolvedTenor = new Tenor(Period.between(curveDate, maturity.toLocalDate()));
      if (strip.getInstrumentType() == StripInstrumentType.FUTURE) {
        securityStrips.add(new FixedIncomeStripWithSecurity(strip.getInstrumentType(), strip.getMaturity(),
            resolvedTenor,
                                                            strip.getNumberOfFuturesAfterTenor(), maturity, strip
                                                                .getSecurity(), security));
      } else {
        securityStrips.add(new FixedIncomeStripWithSecurity(strip.getInstrumentType(), strip.getMaturity(),
            resolvedTenor,
                           maturity, strip.getSecurity(), security));
      }
    }
    return new InterpolatedYieldCurveSpecificationWithSecurities(curveDate, curveSpecification.getName(),
        curveSpecification.getCurrency(), curveSpecification.getInterpolator(), securityStrips);
  }

  private CashSecurity getCash(final InterpolatedYieldCurveSpecification spec, final FixedIncomeStripWithIdentifier strip, final Map<Identifier, Double> marketValues) {
    //    CashSecurity sec = new CashSecurity(spec.getCurrency(), RegionUtils.countryRegionId("US"), 
    //                                        new DateTimeWithZone(spec.getCurveDate().plus(strip.getMaturity().getPeriod()).atTime(11, 00)));
    final CashSecurity sec = new CashSecurity(spec.getCurrency(), spec.getRegion(),
        spec.getCurveDate().plus(strip.getMaturity().getPeriod()).atTime(11, 00).atZone(TimeZone.UTC), marketValues.get(strip.getSecurity()), 1.0d);
    sec.setIdentifiers(IdentifierBundle.of(strip.getSecurity()));
    return sec;
  }

  private FRASecurity getFRA(final InterpolatedYieldCurveSpecification spec, final FixedIncomeStripWithIdentifier strip,
      final Map<Identifier, Double> marketValues) {
    final LocalDate curveDate = spec.getCurveDate(); // quick hack
    final LocalDate startDate = curveDate.plus(strip.getMaturity().getPeriod()).minus(Period.ofMonths(3));
    final LocalDate endDate = startDate.plusMonths(3); // quick hack, needs to be sorted.
    //    return new FRASecurity(spec.getCurrency(), RegionUtils.countryRegionId("US"), 
    //                           new DateTimeWithZone(startDate.atTime(11, 00)), new DateTimeWithZone(endDate.atTime(11, 00)));
    return new FRASecurity(spec.getCurrency(), spec.getRegion(),
        startDate.atTime(11, 00).atZone(TimeZone.UTC), endDate.atTime(11, 00).atZone(TimeZone.UTC), marketValues.get(strip.getSecurity()), 1.0d);
  }

  private FutureSecurity getFuture(final InterpolatedYieldCurveSpecification spec, final FixedIncomeStripWithIdentifier strip) {
    return (FutureSecurity) _secSource.getSecurity(IdentifierBundle.of(strip.getSecurity()));
  }

  private SwapSecurity getSwap(final InterpolatedYieldCurveSpecification spec, final FixedIncomeStripWithIdentifier strip,
      final Map<Identifier, Double> marketValues) {
    final Identifier swapIdentifier = strip.getSecurity();
    final Double rate = marketValues.get(swapIdentifier);
    final LocalDate curveDate = spec.getCurveDate();
    final InMemoryConventionBundleMaster refRateRepo = new InMemoryConventionBundleMaster();
    final ConventionBundleSource source = new DefaultConventionBundleSource(refRateRepo);
    final ZonedDateTime tradeDate = curveDate.atTime(11, 00).atZone(TimeZone.UTC);
    final ZonedDateTime effectiveDate = DateUtil.previousWeekDay(curveDate.plusDays(3)).atTime(11, 00).atZone(TimeZone.UTC);
    final ZonedDateTime maturityDate = curveDate.plus(strip.getMaturity().getPeriod()).atTime(11, 00).atZone(TimeZone.UTC);
    final ConventionBundle convention = _conventionBundleSource.getConventionBundle(Identifier.of(InMemoryConventionBundleMaster.SIMPLE_NAME_SCHEME, spec.getCurrency().getCode() + "_SWAP"));
    final String counterparty = "";
    final ConventionBundle floatRateConvention = source.getConventionBundle(convention.getSwapFloatingLegInitialRate());
    final Identifier floatRateBloombergTicker = Identifier.of(SecurityUtils.BLOOMBERG_TICKER, floatRateConvention.getIdentifiers().getIdentifier(SecurityUtils.BLOOMBERG_TICKER));
    Double initialRate = null;
    for (final Identifier identifier : floatRateConvention.getIdentifiers()) {
      if (marketValues.containsKey(identifier)) {
        initialRate = marketValues.get(identifier); // get the initial rate.
        break;
      }
    }
    if (initialRate == null) {
      throw new OpenGammaRuntimeException("Could not get initial rate");
    }
    final double spread = 0;
    if (rate == null) {
      throw new OpenGammaRuntimeException("rate was null on " + strip + " from " + spec);
    }
    final double fixedRate = rate;
    // REVIEW: jim 25-Aug-2010 -- we need to change the swap to take settlement days.
    final SwapSecurity swap = new SwapSecurity(tradeDate,
                                          effectiveDate,
                                          maturityDate,
                                          counterparty,
                                            new FloatingInterestRateLeg(
                                                convention.getSwapFloatingLegDayCount(),
                                                convention.getSwapFloatingLegFrequency(),
                                                convention.getSwapFloatingLegRegion(),
                                                convention.getSwapFloatingLegBusinessDayConvention(),
                                                new InterestRateNotional(spec.getCurrency(), 1),
                                                floatRateBloombergTicker,
                                                initialRate,
                                                spread),
                                            new FixedInterestRateLeg(
                                                convention.getSwapFixedLegDayCount(),
                                                convention.getSwapFixedLegFrequency(),
                                                convention.getSwapFixedLegRegion(),
                                                convention.getSwapFixedLegBusinessDayConvention(),
                                                new InterestRateNotional(spec.getCurrency(), 1),
                                                fixedRate));
    swap.setIdentifiers(IdentifierBundle.of(swapIdentifier));
    return swap;
  }

  private SwapSecurity getTenorSwap(final InterpolatedYieldCurveSpecification spec, final FixedIncomeStripWithIdentifier strip,
      final Map<Identifier, Double> marketValues) {
    final Identifier swapIdentifier = strip.getSecurity();
    final Double rate = marketValues.get(swapIdentifier);
    final LocalDate curveDate = spec.getCurveDate();
    final InMemoryConventionBundleMaster refRateRepo = new InMemoryConventionBundleMaster();
    final ConventionBundleSource source = new DefaultConventionBundleSource(refRateRepo);
    final ZonedDateTime tradeDate = curveDate.atTime(11, 00).atZone(TimeZone.UTC);
    final ZonedDateTime effectiveDate = DateUtil.previousWeekDay(curveDate.plusDays(3)).atTime(11, 00).atZone(TimeZone.UTC);
    final ZonedDateTime maturityDate = curveDate.plus(strip.getMaturity().getPeriod()).atTime(11, 00).atZone(TimeZone.UTC);
    final ConventionBundle convention = _conventionBundleSource.getConventionBundle(Identifier.of(InMemoryConventionBundleMaster.SIMPLE_NAME_SCHEME, spec.getCurrency().getCode() + "_TENOR_SWAP"));
    final String counterparty = "";
    final ConventionBundle payLegFloatRateConvention = source.getConventionBundle(convention.getBasisSwapPayFloatingLegInitialRate());
    final ConventionBundle receiveLegFloatRateConvention = source.getConventionBundle(convention.getBasisSwapReceiveFloatingLegInitialRate());
    final Identifier payLegFloatRateBloombergTicker = Identifier.of(SecurityUtils.BLOOMBERG_TICKER, payLegFloatRateConvention.getIdentifiers().getIdentifier(SecurityUtils.BLOOMBERG_TICKER));
    final Identifier receiveLegFloatRateBloombergTicker = Identifier.of(SecurityUtils.BLOOMBERG_TICKER, receiveLegFloatRateConvention.getIdentifiers().getIdentifier(SecurityUtils.BLOOMBERG_TICKER));
    if (rate == null) {
      throw new OpenGammaRuntimeException("Could not get spread; was trying " + swapIdentifier);
    }
    final double spread = rate / 10000; //TODO this conversion should not be done here
    //double fixedRate = rate;
    // REVIEW: jim 25-Aug-2010 -- we need to change the swap to take settlement days.
    final SwapSecurity swap = new SwapSecurity(tradeDate,
                                          effectiveDate,
                                          maturityDate,
                                          counterparty,
                                            new FloatingInterestRateLeg(
                                                convention.getBasisSwapPayFloatingLegDayCount(),
                                                convention.getBasisSwapPayFloatingLegFrequency(),
                                                convention.getBasisSwapPayFloatingLegRegion(),
                                                convention.getBasisSwapPayFloatingLegBusinessDayConvention(),
                                                new InterestRateNotional(spec.getCurrency(), 1),
                                                payLegFloatRateBloombergTicker,
                                                0,
                                                0),
                                            new FloatingInterestRateLeg(
                                                convention.getBasisSwapReceiveFloatingLegDayCount(),
                                                convention.getBasisSwapReceiveFloatingLegFrequency(),
                                                convention.getBasisSwapReceiveFloatingLegRegion(),
                                                convention.getBasisSwapReceiveFloatingLegBusinessDayConvention(),
                                                new InterestRateNotional(spec.getCurrency(), 1),
                                                receiveLegFloatRateBloombergTicker,
                                                0,
                                                spread));
    swap.setIdentifiers(IdentifierBundle.of(swapIdentifier));
    return swap;
  }

  private TimeZone ensureZone(final TimeZone zone) {
    if (zone != null) {
      return zone;
    } else {
      return TimeZone.UTC;
    }
  }
}
