/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.analytics.financial.interestrate;

import static org.testng.AssertJUnit.assertEquals;

import javax.time.calendar.Period;

import org.testng.annotations.Test;

import com.opengamma.analytics.financial.instrument.index.IborIndex;
import com.opengamma.analytics.financial.interestrate.annuity.derivative.Annuity;
import com.opengamma.analytics.financial.interestrate.annuity.derivative.AnnuityCouponFixed;
import com.opengamma.analytics.financial.interestrate.annuity.derivative.AnnuityCouponIborSpread;
import com.opengamma.analytics.financial.interestrate.annuity.derivative.AnnuityPaymentFixed;
import com.opengamma.analytics.financial.interestrate.bond.definition.BondFixedSecurity;
import com.opengamma.analytics.financial.interestrate.cash.derivative.Cash;
import com.opengamma.analytics.financial.interestrate.fra.ForwardRateAgreement;
import com.opengamma.analytics.financial.interestrate.future.derivative.InterestRateFuture;
import com.opengamma.analytics.financial.interestrate.payments.derivative.CouponIborSpread;
import com.opengamma.analytics.financial.interestrate.payments.derivative.PaymentFixed;
import com.opengamma.analytics.financial.interestrate.swap.derivative.FixedFloatSwap;
import com.opengamma.analytics.financial.interestrate.swap.derivative.Swap;
import com.opengamma.analytics.financial.interestrate.swap.derivative.TenorSwap;
import com.opengamma.analytics.financial.model.interestrate.curve.YieldAndDiscountCurve;
import com.opengamma.analytics.financial.model.interestrate.curve.YieldCurve;
import com.opengamma.analytics.math.curve.ConstantDoublesCurve;
import com.opengamma.financial.convention.businessday.BusinessDayConvention;
import com.opengamma.financial.convention.businessday.BusinessDayConventionFactory;
import com.opengamma.financial.convention.calendar.Calendar;
import com.opengamma.financial.convention.calendar.MondayToFridayCalendar;
import com.opengamma.financial.convention.daycount.DayCount;
import com.opengamma.financial.convention.daycount.DayCountFactory;
import com.opengamma.financial.convention.yield.SimpleYieldConvention;
import com.opengamma.util.money.Currency;

/**
 * 
 */
public class ParRateCalculatorTest {
  private static final ParRateCalculator PRC = ParRateCalculator.getInstance();
  private static final PresentValueCalculator PVC = PresentValueCalculator.getInstance();
  private static final String FIVE_PC_CURVE_NAME = "5%";
  private static final String ZERO_PC_CURVE_NAME = "0%";
  private static final YieldCurveBundle CURVES;
  private static final Currency CUR = Currency.USD;

  private static final Period TENOR = Period.ofMonths(6);
  private static final int SETTLEMENT_DAYS = 2;
  private static final Calendar CALENDAR = new MondayToFridayCalendar("A");
  private static final DayCount DAY_COUNT_INDEX = DayCountFactory.INSTANCE.getDayCount("Actual/360");
  private static final BusinessDayConvention BUSINESS_DAY = BusinessDayConventionFactory.INSTANCE.getBusinessDayConvention("Modified Following");
  private static final boolean IS_EOM = true;
  private static final IborIndex INDEX = new IborIndex(CUR, TENOR, SETTLEMENT_DAYS, CALENDAR, DAY_COUNT_INDEX, BUSINESS_DAY, IS_EOM);

  static {
    YieldAndDiscountCurve curve = YieldCurve.from(ConstantDoublesCurve.from(0.05));
    CURVES = new YieldCurveBundle();
    CURVES.setCurve(FIVE_PC_CURVE_NAME, curve);
    curve = YieldCurve.from(ConstantDoublesCurve.from(0.0));
    CURVES.setCurve(ZERO_PC_CURVE_NAME, curve);
  }

  @Test
  public void testCash() {
    final double t = 7 / 365.0;
    final double tradeTime = 2.0 / 365.0;
    final double yearFrac = 5.0 / 360.0;

    Cash cash = new Cash(CUR, tradeTime, t, 1, 0, yearFrac, FIVE_PC_CURVE_NAME);
    final double rate = cash.accept(PRC, CURVES);
    cash = new Cash(CUR, tradeTime, t, 1, rate, yearFrac, FIVE_PC_CURVE_NAME);
    assertEquals(0.0, cash.accept(PVC, CURVES), 1e-12);
  }

  @Test
  public void testFRA() {
    final double paymentTime = 0.5;
    final double paymentYearFraction = 30. / 360;
    final double fixingTime = paymentTime - 2. / 365;
    final double fixingPeriodStartTime = paymentTime;
    final double fixingPeriodEndTime = 7. / 12;
    final double fixingYearFraction = 31. / 365;
    final IborIndex index = new IborIndex(CUR, Period.ofMonths(1), 2, new MondayToFridayCalendar("A"), DayCountFactory.INSTANCE.getDayCount("Actual/365"),
        BusinessDayConventionFactory.INSTANCE.getBusinessDayConvention("Following"), true);
    ForwardRateAgreement fra = new ForwardRateAgreement(CUR, paymentTime, FIVE_PC_CURVE_NAME, paymentYearFraction, 1, index, fixingTime, fixingPeriodStartTime, fixingPeriodEndTime,
        fixingYearFraction, 0, FIVE_PC_CURVE_NAME);
    final double rate = fra.accept(PRC, CURVES);
    fra = new ForwardRateAgreement(CUR, paymentTime, FIVE_PC_CURVE_NAME, paymentYearFraction, 1, index, fixingTime, fixingPeriodStartTime, fixingPeriodEndTime, fixingYearFraction, rate,
        FIVE_PC_CURVE_NAME);
    assertEquals(0.0, fra.accept(PVC, CURVES), 1e-12);
  }

  @Test
  public void testFutures() {
    final IborIndex iborIndex = new IborIndex(CUR, Period.ofMonths(3), 2, new MondayToFridayCalendar("A"), DayCountFactory.INSTANCE.getDayCount("Actual/360"),
        BusinessDayConventionFactory.INSTANCE.getBusinessDayConvention("Following"), true);
    final double lastTradingTime = 1.453;
    final double fixingPeriodStartTime = lastTradingTime;
    final double fixingPeriodEndTime = 1.75;
    final double fixingPeriodAccrualFactor = 0.267;
    final double notional = 1000000;
    final double paymentAccrualFactor = 0.25;
    final int quantity = 123;
    final double referencePrice = 0.0; // TODO CASE - Future refactor - referencePrice = 0.0
    final String name = "name";
    final InterestRateFuture ir = new InterestRateFuture(lastTradingTime, iborIndex, fixingPeriodStartTime, fixingPeriodEndTime, fixingPeriodAccrualFactor, referencePrice, notional,
        paymentAccrualFactor, quantity, name, FIVE_PC_CURVE_NAME, FIVE_PC_CURVE_NAME);
    final double rate = ir.accept(PRC, CURVES);
    final double price = 1 - rate;
    //final InterestRateFutureTransaction traded = new InterestRateFutureTransaction(ir, 1, price);
    final double pvExpected = price * notional * paymentAccrualFactor * quantity;
    final double pv = ir.accept(PVC, CURVES);
    assertEquals(pvExpected, pv, 1e-12);
  }

  @Test
  public void testBond() {
    final int n = 20;
    final double tau = 0.5;
    final double yearFrac = 180 / 365.0;
    final double[] yearFracs = new double[n];
    final double[] paymentTimes = new double[n];
    for (int i = 0; i < n; i++) {
      paymentTimes[i] = tau * (i + 1);
      yearFracs[i] = yearFrac;
    }
    final AnnuityPaymentFixed nominal = new AnnuityPaymentFixed(new PaymentFixed[] {new PaymentFixed(CUR, 11, 1, FIVE_PC_CURVE_NAME) });
    AnnuityCouponFixed coupon = new AnnuityCouponFixed(CUR, new double[] {0.5, 1 }, 0.03, FIVE_PC_CURVE_NAME, false);
    BondFixedSecurity bond = new BondFixedSecurity(nominal, coupon, 0, 0, 0.5, SimpleYieldConvention.TRUE, 2, FIVE_PC_CURVE_NAME, "S");
    final double rate = bond.accept(PRC, CURVES);
    coupon = new AnnuityCouponFixed(CUR, new double[] {0.5, 1 }, rate, FIVE_PC_CURVE_NAME, false);
    bond = new BondFixedSecurity(nominal, coupon, 0, 0, 0.5, SimpleYieldConvention.TRUE, 2, FIVE_PC_CURVE_NAME, "S");
    assertEquals(1.0, bond.accept(PVC, CURVES), 1e-12);
  }

  @Test
  public void testFixedFloatSwap() {
    final int n = 20;
    final double[] fixedPaymentTimes = new double[n];
    final double[] floatPaymentTimes = new double[2 * n];

    for (int i = 0; i < n * 2; i++) {
      if (i % 2 == 0) {
        fixedPaymentTimes[i / 2] = (i + 2) * 0.25;
      }
      floatPaymentTimes[i] = (i + 1) * 0.25;
    }

    Swap<?, ?> swap = new FixedFloatSwap(CUR, fixedPaymentTimes, floatPaymentTimes, INDEX, 0.0, FIVE_PC_CURVE_NAME, FIVE_PC_CURVE_NAME, true);
    final double rate = swap.accept(PRC, CURVES);
    swap = new FixedFloatSwap(CUR, fixedPaymentTimes, floatPaymentTimes, INDEX, rate, FIVE_PC_CURVE_NAME, FIVE_PC_CURVE_NAME, true);
    assertEquals(0.0, swap.accept(PVC, CURVES), 1e-12);
  }

  @Test
  public void testBasisSwap() {
    final int n = 20;
    final double tau = 0.25;
    final double[] paymentTimes = new double[n];
    final double[] yearFracs = new double[n];
    final double[] indexFixing = new double[n];
    final double[] indexMaturity = new double[n];
    for (int i = 0; i < n; i++) {
      indexFixing[i] = i * tau;
      paymentTimes[i] = (i + 1) * tau;
      indexMaturity[i] = paymentTimes[i];
      yearFracs[i] = tau;
    }

    final Annuity<CouponIborSpread> payLeg = new AnnuityCouponIborSpread(CUR, paymentTimes, indexFixing, INDEX, indexFixing, indexMaturity, yearFracs, yearFracs, new double[yearFracs.length], 1.0,
        FIVE_PC_CURVE_NAME, ZERO_PC_CURVE_NAME, true);
    final Annuity<CouponIborSpread> receiveLeg = new AnnuityCouponIborSpread(CUR, paymentTimes, indexFixing, INDEX, indexFixing, indexMaturity, yearFracs, yearFracs, new double[yearFracs.length],
        1.0,
        FIVE_PC_CURVE_NAME, FIVE_PC_CURVE_NAME, false);

    Swap<?, ?> swap = new TenorSwap<CouponIborSpread>(payLeg, receiveLeg);
    final double rate = swap.accept(PRC, CURVES);
    final double[] spreads = new double[n];
    for (int i = 0; i < n; i++) {
      spreads[i] = rate;
    }
    final Annuity<CouponIborSpread> payLeg2 = new AnnuityCouponIborSpread(CUR, paymentTimes, indexFixing, INDEX, indexFixing, indexMaturity, yearFracs, yearFracs, new double[yearFracs.length], 1.0,
        FIVE_PC_CURVE_NAME, ZERO_PC_CURVE_NAME, true);
    final Annuity<CouponIborSpread> receiveLeg2 = new AnnuityCouponIborSpread(CUR, paymentTimes, indexFixing, INDEX, indexFixing, indexMaturity, yearFracs, yearFracs, spreads, 1.0,
        FIVE_PC_CURVE_NAME,
        FIVE_PC_CURVE_NAME, false);
    swap = new TenorSwap<CouponIborSpread>(payLeg2, receiveLeg2);
    assertEquals(0.0, swap.accept(PVC, CURVES), 1e-12);
  }
}
