/**
 * Copyright (C) 2011 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.analytics.financial.interestrate.bond.method;

import org.apache.commons.lang.Validate;

import com.opengamma.analytics.financial.instrument.inflation.CouponInflationGearing;
import com.opengamma.analytics.financial.interestrate.InstrumentDerivative;
import com.opengamma.analytics.financial.interestrate.PresentValueInflationCalculator;
import com.opengamma.analytics.financial.interestrate.bond.definition.BondCapitalIndexedSecurity;
import com.opengamma.analytics.financial.interestrate.market.description.IMarketBundle;
import com.opengamma.analytics.financial.interestrate.market.description.MarketDiscountBundle;
import com.opengamma.analytics.financial.interestrate.market.description.MarketDiscountBundleDiscountingDecorated;
import com.opengamma.analytics.financial.interestrate.method.PricingMarketMethod;
import com.opengamma.analytics.financial.interestrate.payments.derivative.Coupon;
import com.opengamma.analytics.math.function.Function1D;
import com.opengamma.analytics.math.rootfinding.BracketRoot;
import com.opengamma.analytics.math.rootfinding.BrentSingleRootFinder;
import com.opengamma.analytics.math.rootfinding.RealSingleRootFinder;
import com.opengamma.financial.convention.yield.SimpleYieldConvention;
import com.opengamma.util.ArgumentChecker;
import com.opengamma.util.money.MultipleCurrencyAmount;

/**
 * Pricing method for inflation bond. The price is computed by index estimation and discounting.
 */
public final class BondCapitalIndexedSecurityDiscountingMethod implements PricingMarketMethod {

  /**
   * The present value inflation calculator (for the different parts of the bond transaction).
   */
  private static final PresentValueInflationCalculator PVIC = PresentValueInflationCalculator.getInstance();
  //TODO: REVIEW: Method depends on Calculator; Calculator would depend on Method (code duplicated to avoid circularity).
  /**
   * The root bracket used for yield finding.
   */
  private static final BracketRoot BRACKETER = new BracketRoot();
  /**
   * The root finder used for yield finding.
   */
  private static final RealSingleRootFinder ROOT_FINDER = new BrentSingleRootFinder();

  /**
   * Computes the present value of a capital indexed bound by index estimation and discounting. The value is the value of the nominal and the coupons but not the settlement.
   * @param bond The bond.
   * @param market The market.
   * @return The present value.
   */
  public MultipleCurrencyAmount presentValue(final BondCapitalIndexedSecurity<?> bond, final MarketDiscountBundle market) {
    ArgumentChecker.notNull(bond, "Bond");
    MarketDiscountBundle creditDiscounting = new MarketDiscountBundleDiscountingDecorated(market, bond.getCurrency(), market.getCurve(bond.getIssuerCurrency()));
    final MultipleCurrencyAmount pvNominal = PVIC.visit(bond.getNominal(), creditDiscounting);
    final MultipleCurrencyAmount pvCoupon = PVIC.visit(bond.getCoupon(), creditDiscounting);
    return pvNominal.plus(pvCoupon);
  }

  @Override
  public MultipleCurrencyAmount presentValue(InstrumentDerivative instrument, IMarketBundle market) {
    Validate.isTrue(instrument instanceof BondCapitalIndexedSecurity<?>, "Capital inflation indexed bond.");
    return presentValue((BondCapitalIndexedSecurity<?>) instrument, (MarketDiscountBundle) market);
  }

  /**
   * Computes the security present value from a quoted clean real price. The real accrued are added to the clean real price, 
   * the result is multiplied by the inflation index ratio and then discounted from settlement time to 0 with the discounting curve.
   * @param bond The bond security.
   * @param market The market.
   * @param cleanPriceReal The clean price.
   * @return The present value.
   */
  public MultipleCurrencyAmount presentValueFromCleanPriceReal(final BondCapitalIndexedSecurity<Coupon> bond, final MarketDiscountBundle market, final double cleanPriceReal) {
    Validate.notNull(bond, "Coupon");
    Validate.notNull(market, "Market");
    final double notional = bond.getCoupon().getNthPayment(0).getNotional();
    double dirtyPriceReal = cleanPriceReal + bond.getAccruedInterest() / notional;
    double estimatedIndex = bond.getSettlement().estimatedIndex(market);
    double dirtyPriceAjusted = dirtyPriceReal * estimatedIndex / bond.getIndexStartValue();
    double dfSettle = market.getDiscountFactor(bond.getCurrency(), bond.getSettlementTime());
    double pv = dirtyPriceAjusted * bond.getCoupon().getNthPayment(0).getNotional() * dfSettle;
    return MultipleCurrencyAmount.of(bond.getCurrency(), pv);
  }

  /**
   * Computes the clean real price of a bond security from a dirty real price.
   * @param bond The bond security.
   * @param dirtyPrice The dirty price.
   * @return The clean price.
   */
  public double cleanRealPriceFromDirtyRealPrice(final BondCapitalIndexedSecurity<?> bond, final double dirtyPrice) {
    final double notional = bond.getCoupon().getNthPayment(0).getNotional();
    return dirtyPrice - bond.getAccruedInterest() / notional;
  }

  /**
   * Computes the dirty real price of a bond security from the clean real price.
   * @param bond The bond security.
   * @param cleanPrice The clean price.
   * @return The clean price.
   */
  public double dirtyRealPriceFromCleanRealPrice(final BondCapitalIndexedSecurity<?> bond, final double cleanPrice) {
    final double notional = bond.getCoupon().getNthPayment(0).getNotional();
    return cleanPrice + bond.getAccruedInterest() / notional;
  }

  /**
   * The net amount paid at settlement date for a given clean real price.
   * @param bond The bond.
   * @param market The market.
   * @param cleanPriceReal The clean real price.
   * @return The net amount.
   */
  public double netAmount(final BondCapitalIndexedSecurity<Coupon> bond, final MarketDiscountBundle market, final double cleanPriceReal) {
    final double notional = bond.getCoupon().getNthPayment(0).getNotional();
    double netAmountReal = cleanPriceReal * notional + bond.getAccruedInterest();
    double estimatedIndex = bond.getSettlement().estimatedIndex(market);
    double netAmount = netAmountReal * estimatedIndex / bond.getIndexStartValue();
    return netAmount;
  }

  /**
   * Computes the dirty real price from the conventional real yield.
   * @param bond  The bond security.
   * @param yield The bond yield.
   * @return The dirty price.
   */
  public double dirtyRealPriceFromYieldReal(final BondCapitalIndexedSecurity<?> bond, final double yield) {
    Validate.isTrue(bond.getNominal().getNumberOfPayments() == 1, "Yield: more than one nominal repayment.");
    final int nbCoupon = bond.getCoupon().getNumberOfPayments();
    if (bond.getYieldConvention().equals(SimpleYieldConvention.US_IL_REAL)) {
      // Coupon period rate to next coupon and simple rate from next coupon to settlement.
      double pvAtFirstCoupon;
      if (Math.abs(yield) > 1.0E-8) {
        final double factorOnPeriod = 1 + yield / bond.getCouponPerYear();
        double vn = Math.pow(factorOnPeriod, 1 - nbCoupon);
        pvAtFirstCoupon = ((CouponInflationGearing) bond.getCoupon().getNthPayment(0)).getFactor() / yield * (factorOnPeriod - vn) + vn;
      } else {
        pvAtFirstCoupon = ((CouponInflationGearing) bond.getCoupon().getNthPayment(0)).getFactor() / bond.getCouponPerYear() * nbCoupon + 1;
      }
      return pvAtFirstCoupon / (1 + bond.getAccrualFactorToNextCoupon() * yield / bond.getCouponPerYear());
    }
    throw new UnsupportedOperationException("The convention " + bond.getYieldConvention().getConventionName() + " is not supported.");
  }

  /**
   * Compute the conventional yield from the dirty price.
   * @param bond The bond security.
   * @param dirtyPrice The bond dirty price.
   * @return The yield.
   */
  public double yieldRealFromDirtyRealPrice(final BondCapitalIndexedSecurity<?> bond, final double dirtyPrice) {
    /**
     * Inner function used to find the yield.
     */
    final Function1D<Double, Double> priceResidual = new Function1D<Double, Double>() {
      @Override
      public Double evaluate(final Double y) {
        return dirtyRealPriceFromYieldReal(bond, y) - dirtyPrice;
      }
    };
    final double[] range = BRACKETER.getBracketedPoints(priceResidual, -0.05, 0.10);
    final double yield = ROOT_FINDER.getRoot(priceResidual, range[0], range[1]);
    return yield;
  }

  // TODO: curve sensitivity
  // TODO: price index sensitivity

}
