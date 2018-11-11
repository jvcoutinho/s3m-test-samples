/**
 * Copyright (C) 2011 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.financial.instrument.inflation;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;

import javax.time.calendar.Period;
import javax.time.calendar.ZonedDateTime;

import org.testng.annotations.Test;

import com.opengamma.financial.convention.businessday.BusinessDayConvention;
import com.opengamma.financial.convention.businessday.BusinessDayConventionFactory;
import com.opengamma.financial.convention.calendar.Calendar;
import com.opengamma.financial.convention.calendar.MondayToFridayCalendar;
import com.opengamma.financial.convention.daycount.DayCount;
import com.opengamma.financial.convention.daycount.DayCountFactory;
import com.opengamma.financial.instrument.index.PriceIndex;
import com.opengamma.financial.interestrate.inflation.derivatives.CouponInflationZeroCoupon;
import com.opengamma.financial.interestrate.payments.Coupon;
import com.opengamma.financial.interestrate.payments.CouponFixed;
import com.opengamma.financial.schedule.ScheduleCalculator;
import com.opengamma.util.money.Currency;
import com.opengamma.util.time.DateUtil;
import com.opengamma.util.timeseries.DoubleTimeSeries;
import com.opengamma.util.timeseries.zoneddatetime.ArrayZonedDateTimeDoubleTimeSeries;

/**
 * Tests the zero-coupon inflation constructors.
 */
public class CouponInflationZeroCouponDefinitionTest {
  private static final String NAME = "Euro HICP x";
  private static final Currency CUR = Currency.EUR;
  private static final Currency REGION = Currency.EUR;
  private static final PriceIndex PRICE_INDEX = new PriceIndex(NAME, CUR, REGION);
  private static final Calendar CALENDAR = new MondayToFridayCalendar("A");
  private static final BusinessDayConvention BUSINESS_DAY = BusinessDayConventionFactory.INSTANCE.getBusinessDayConvention("Modified Following");
  private static final ZonedDateTime START_DATE = DateUtil.getUTCDate(2008, 8, 18);
  private static final Period COUPON_TENOR = Period.ofYears(10);
  private static final ZonedDateTime PAYMENT_DATE = ScheduleCalculator.getAdjustedDate(START_DATE, BUSINESS_DAY, CALENDAR, COUPON_TENOR);
  private static final ZonedDateTime ACCRUAL_END_DATE = PAYMENT_DATE.minusDays(1); // For getter test
  private static final double NOTIONAL = 98765432;
  private static final int MONTH_LAG = 3;
  private static final double INDEX_APRIL_2008 = 108.23; // 3 m before Aug: May / 1 May index = May index: 108.23
  private static final ZonedDateTime REFERENCE_START_DATE = DateUtil.getUTCDate(2008, 5, 1);
  private static final ZonedDateTime REFERENCE_END_DATE = PAYMENT_DATE.minusMonths(MONTH_LAG).withDayOfMonth(1);
  private static final ZonedDateTime FIXING_DATE = REFERENCE_END_DATE.plusMonths(1).withDayOfMonth(1).plusWeeks(2);
  private static final CouponInflationZeroCouponDefinition ZERO_COUPON_DEFINITION = new CouponInflationZeroCouponDefinition(CUR, PAYMENT_DATE, START_DATE, ACCRUAL_END_DATE, 1.0, NOTIONAL,
      PRICE_INDEX, REFERENCE_START_DATE, INDEX_APRIL_2008, REFERENCE_END_DATE, FIXING_DATE);
  private static final String DISCOUNTING_CURVE_NAME = "Discounting";
  private static final String PRICE_INDEX_CURVE_NAME = "Price index";
  private static final String[] CURVE_NAMES = new String[] {DISCOUNTING_CURVE_NAME, PRICE_INDEX_CURVE_NAME};
  private static final DayCount ACT_ACT = DayCountFactory.INSTANCE.getDayCount("Actual/Actual ISDA");

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNullCurrency() {
    new CouponInflationZeroCouponDefinition(null, PAYMENT_DATE, START_DATE, PAYMENT_DATE, 1.0, NOTIONAL, PRICE_INDEX, REFERENCE_START_DATE, INDEX_APRIL_2008, REFERENCE_END_DATE, FIXING_DATE);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNullPay() {
    new CouponInflationZeroCouponDefinition(CUR, null, START_DATE, PAYMENT_DATE, 1.0, NOTIONAL, PRICE_INDEX, REFERENCE_START_DATE, INDEX_APRIL_2008, REFERENCE_END_DATE, FIXING_DATE);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNullStart() {
    new CouponInflationZeroCouponDefinition(CUR, PAYMENT_DATE, null, PAYMENT_DATE, 1.0, NOTIONAL, PRICE_INDEX, REFERENCE_START_DATE, INDEX_APRIL_2008, REFERENCE_END_DATE, FIXING_DATE);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNullEnd() {
    new CouponInflationZeroCouponDefinition(CUR, PAYMENT_DATE, START_DATE, null, 1.0, NOTIONAL, PRICE_INDEX, REFERENCE_START_DATE, INDEX_APRIL_2008, REFERENCE_END_DATE, FIXING_DATE);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNullIndex() {
    new CouponInflationZeroCouponDefinition(CUR, PAYMENT_DATE, START_DATE, PAYMENT_DATE, 1.0, NOTIONAL, null, REFERENCE_START_DATE, INDEX_APRIL_2008, REFERENCE_END_DATE, FIXING_DATE);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNullRefStart() {
    new CouponInflationZeroCouponDefinition(CUR, PAYMENT_DATE, START_DATE, PAYMENT_DATE, 1.0, NOTIONAL, PRICE_INDEX, null, INDEX_APRIL_2008, REFERENCE_END_DATE, FIXING_DATE);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNullRefEnd() {
    new CouponInflationZeroCouponDefinition(CUR, PAYMENT_DATE, START_DATE, PAYMENT_DATE, 1.0, NOTIONAL, PRICE_INDEX, REFERENCE_START_DATE, INDEX_APRIL_2008, null, FIXING_DATE);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNullFixing() {
    new CouponInflationZeroCouponDefinition(CUR, PAYMENT_DATE, START_DATE, PAYMENT_DATE, 1.0, NOTIONAL, PRICE_INDEX, REFERENCE_START_DATE, INDEX_APRIL_2008, REFERENCE_END_DATE, null);
  }

  @Test
  /**
   * Tests the class getter.
   */
  public void getter() {
    assertEquals("Inflation Zero-coupon: getter", CUR, ZERO_COUPON_DEFINITION.getCurrency());
    assertEquals("Inflation Zero-coupon: getter", PAYMENT_DATE, ZERO_COUPON_DEFINITION.getPaymentDate());
    assertEquals("Inflation Zero-coupon: getter", START_DATE, ZERO_COUPON_DEFINITION.getAccrualStartDate());
    assertEquals("Inflation Zero-coupon: getter", ACCRUAL_END_DATE, ZERO_COUPON_DEFINITION.getAccrualEndDate());
    assertEquals("Inflation Zero-coupon: getter", 1.0, ZERO_COUPON_DEFINITION.getPaymentYearFraction());
    assertEquals("Inflation Zero-coupon: getter", NOTIONAL, ZERO_COUPON_DEFINITION.getNotional());
    assertEquals("Inflation Zero-coupon: getter", PRICE_INDEX, ZERO_COUPON_DEFINITION.getPriceIndex());
    assertEquals("Inflation Zero-coupon: getter", REFERENCE_START_DATE, ZERO_COUPON_DEFINITION.getReferenceStartDate());
    assertEquals("Inflation Zero-coupon: getter", REFERENCE_END_DATE, ZERO_COUPON_DEFINITION.getReferenceEndDate());
    assertEquals("Inflation Zero-coupon: getter", INDEX_APRIL_2008, ZERO_COUPON_DEFINITION.getIndexStartValue());
    assertEquals("Inflation Zero-coupon: getter", FIXING_DATE, ZERO_COUPON_DEFINITION.getFixingEndDate());
  }

  @Test
  /**
   * Tests the equal and hash-code methods.
   */
  public void equalHash() {
    assertEquals(ZERO_COUPON_DEFINITION, ZERO_COUPON_DEFINITION);
    CouponInflationZeroCouponDefinition couponDuplicate = new CouponInflationZeroCouponDefinition(CUR, PAYMENT_DATE, START_DATE, ACCRUAL_END_DATE, 1.0, NOTIONAL, PRICE_INDEX, REFERENCE_START_DATE,
        INDEX_APRIL_2008, REFERENCE_END_DATE, FIXING_DATE);
    assertEquals(ZERO_COUPON_DEFINITION, couponDuplicate);
    assertEquals(ZERO_COUPON_DEFINITION.hashCode(), couponDuplicate.hashCode());
    CouponInflationZeroCouponDefinition modified;
    modified = new CouponInflationZeroCouponDefinition(CUR, ACCRUAL_END_DATE, START_DATE, ACCRUAL_END_DATE, 1.0, NOTIONAL, PRICE_INDEX, REFERENCE_START_DATE, INDEX_APRIL_2008, REFERENCE_END_DATE,
        FIXING_DATE);
    assertFalse(ZERO_COUPON_DEFINITION.equals(modified));
    modified = new CouponInflationZeroCouponDefinition(CUR, PAYMENT_DATE, START_DATE.minusDays(1), ACCRUAL_END_DATE, 1.0, NOTIONAL, PRICE_INDEX, REFERENCE_START_DATE, INDEX_APRIL_2008,
        REFERENCE_END_DATE, FIXING_DATE);
    assertFalse(ZERO_COUPON_DEFINITION.equals(modified));
    modified = new CouponInflationZeroCouponDefinition(CUR, PAYMENT_DATE, START_DATE, ACCRUAL_END_DATE.minusDays(1), 1.0, NOTIONAL, PRICE_INDEX, REFERENCE_START_DATE, INDEX_APRIL_2008,
        REFERENCE_END_DATE, FIXING_DATE);
    assertFalse(ZERO_COUPON_DEFINITION.equals(modified));
    modified = new CouponInflationZeroCouponDefinition(CUR, PAYMENT_DATE, START_DATE, ACCRUAL_END_DATE, 1.0, NOTIONAL, PRICE_INDEX, REFERENCE_START_DATE.minusDays(1), INDEX_APRIL_2008,
        REFERENCE_END_DATE, FIXING_DATE);
    assertFalse(ZERO_COUPON_DEFINITION.equals(modified));
    modified = new CouponInflationZeroCouponDefinition(CUR, PAYMENT_DATE, START_DATE, ACCRUAL_END_DATE, 1.0, NOTIONAL, PRICE_INDEX, REFERENCE_START_DATE, INDEX_APRIL_2008,
        REFERENCE_END_DATE.minusDays(1), FIXING_DATE);
    assertFalse(ZERO_COUPON_DEFINITION.equals(modified));
    modified = new CouponInflationZeroCouponDefinition(CUR, PAYMENT_DATE, START_DATE, ACCRUAL_END_DATE, 1.0, NOTIONAL, PRICE_INDEX, REFERENCE_START_DATE, INDEX_APRIL_2008, REFERENCE_END_DATE,
        FIXING_DATE.minusDays(1));
    assertFalse(ZERO_COUPON_DEFINITION.equals(modified));
    modified = new CouponInflationZeroCouponDefinition(CUR, PAYMENT_DATE, START_DATE, ACCRUAL_END_DATE, 2.0, NOTIONAL, PRICE_INDEX, REFERENCE_START_DATE, INDEX_APRIL_2008, REFERENCE_END_DATE,
        FIXING_DATE);
    assertFalse(ZERO_COUPON_DEFINITION.equals(modified));
    modified = new CouponInflationZeroCouponDefinition(CUR, PAYMENT_DATE, START_DATE, ACCRUAL_END_DATE, 1.0, NOTIONAL + 10.0, PRICE_INDEX, REFERENCE_START_DATE, INDEX_APRIL_2008, REFERENCE_END_DATE,
        FIXING_DATE);
    assertFalse(ZERO_COUPON_DEFINITION.equals(modified));
    modified = new CouponInflationZeroCouponDefinition(CUR, PAYMENT_DATE, START_DATE, ACCRUAL_END_DATE, 1.0, NOTIONAL, PRICE_INDEX, REFERENCE_START_DATE, INDEX_APRIL_2008 + 1.0, REFERENCE_END_DATE,
        FIXING_DATE);
    assertFalse(ZERO_COUPON_DEFINITION.equals(modified));
  }

  @Test
  /**
   * Tests the first builder.
   */
  public void from1() {
    CouponInflationZeroCouponDefinition constructor = new CouponInflationZeroCouponDefinition(CUR, PAYMENT_DATE, START_DATE, PAYMENT_DATE, 1.0, NOTIONAL, PRICE_INDEX, START_DATE, INDEX_APRIL_2008,
        REFERENCE_END_DATE, FIXING_DATE);
    CouponInflationZeroCouponDefinition from = CouponInflationZeroCouponDefinition.from(START_DATE, PAYMENT_DATE, NOTIONAL, PRICE_INDEX, INDEX_APRIL_2008, REFERENCE_END_DATE, FIXING_DATE);
    assertEquals("Inflation zero-coupon : from", constructor, from);
  }

  @Test
  /**
   * Tests the first based on indexation lag.
   */
  public void from2() {
    CouponInflationZeroCouponDefinition constructor = new CouponInflationZeroCouponDefinition(CUR, PAYMENT_DATE, START_DATE, PAYMENT_DATE, 1.0, NOTIONAL, PRICE_INDEX, REFERENCE_START_DATE,
        INDEX_APRIL_2008, REFERENCE_END_DATE, FIXING_DATE);
    CouponInflationZeroCouponDefinition from = CouponInflationZeroCouponDefinition.from(START_DATE, PAYMENT_DATE, NOTIONAL, PRICE_INDEX, INDEX_APRIL_2008, MONTH_LAG, true);
    assertEquals("Inflation zero-coupon : from", constructor, from);
  }

  @Test
  public void toDerivativesNoData() {
    final ZonedDateTime pricingDate = DateUtil.getUTCDate(2011, 7, 29);
    Coupon zeroCouponConverted = ZERO_COUPON_DEFINITION.toDerivative(pricingDate, CURVE_NAMES);
    double paymentTime = ACT_ACT.getDayCountFraction(pricingDate, PAYMENT_DATE);
    final double referenceEndTime = ACT_ACT.getDayCountFraction(pricingDate, REFERENCE_END_DATE);
    final double fixingTime = ACT_ACT.getDayCountFraction(pricingDate, FIXING_DATE);
    CouponInflationZeroCoupon zeroCoupon = new CouponInflationZeroCoupon(CUR, paymentTime, DISCOUNTING_CURVE_NAME, 1.0, NOTIONAL, PRICE_INDEX, INDEX_APRIL_2008, referenceEndTime, fixingTime,
        PRICE_INDEX_CURVE_NAME);
    assertEquals("Inflation zero-coupon: toDerivative", zeroCouponConverted, zeroCoupon);
  }

  @Test
  public void toDerivativesStartMonthNotknown() {
    final ZonedDateTime pricingDate = DateUtil.getUTCDate(2011, 7, 29);
    final DoubleTimeSeries<ZonedDateTime> priceIndexTS = new ArrayZonedDateTimeDoubleTimeSeries(new ZonedDateTime[] {DateUtil.getUTCDate(2008, 5, 1), DateUtil.getUTCDate(2008, 6, 1)}, new double[] {
        128.23, 128.43});
    Coupon zeroCouponConverted = ZERO_COUPON_DEFINITION.toDerivative(pricingDate, priceIndexTS, CURVE_NAMES);
    double paymentTime = ACT_ACT.getDayCountFraction(pricingDate, PAYMENT_DATE);
    final double referenceEndTime = ACT_ACT.getDayCountFraction(pricingDate, REFERENCE_END_DATE);
    final double fixingTime = ACT_ACT.getDayCountFraction(pricingDate, FIXING_DATE);
    CouponInflationZeroCoupon zeroCoupon = new CouponInflationZeroCoupon(CUR, paymentTime, DISCOUNTING_CURVE_NAME, 1.0, NOTIONAL, PRICE_INDEX, INDEX_APRIL_2008, referenceEndTime, fixingTime,
        PRICE_INDEX_CURVE_NAME);
    assertEquals("Inflation zero-coupon: toDerivative", zeroCoupon, zeroCouponConverted);
  }

  @Test
  public void toDerivativesStartMonthKnown() {
    final ZonedDateTime pricingDate = DateUtil.getUTCDate(2018, 6, 25);
    final DoubleTimeSeries<ZonedDateTime> priceIndexTS = new ArrayZonedDateTimeDoubleTimeSeries(new ZonedDateTime[] {DateUtil.getUTCDate(2018, 5, 1), DateUtil.getUTCDate(2018, 6, 1)}, new double[] {
        128.23, 128.43});
    Coupon zeroCouponConverted = ZERO_COUPON_DEFINITION.toDerivative(pricingDate, priceIndexTS, CURVE_NAMES);
    double paymentTime = ACT_ACT.getDayCountFraction(pricingDate, PAYMENT_DATE);
    CouponFixed zeroCoupon = new CouponFixed(CUR, paymentTime, DISCOUNTING_CURVE_NAME, 1.0, NOTIONAL, 128.23 / INDEX_APRIL_2008 - 1.0);
    assertEquals("Inflation zero-coupon: toDerivative", zeroCoupon, zeroCouponConverted);
  }

  @Test
  public void toDerivativesInterpolatedKnown() {
    final ZonedDateTime pricingDate = DateUtil.getUTCDate(2018, 7, 25);
    final DoubleTimeSeries<ZonedDateTime> priceIndexTS = new ArrayZonedDateTimeDoubleTimeSeries(new ZonedDateTime[] {DateUtil.getUTCDate(2018, 5, 1), DateUtil.getUTCDate(2018, 6, 1)}, new double[] {
        128.23, 128.43});
    CouponInflationZeroCouponDefinition zeroCouponInterpolated = CouponInflationZeroCouponDefinition.from(START_DATE, PAYMENT_DATE, NOTIONAL, PRICE_INDEX, INDEX_APRIL_2008, MONTH_LAG, false);
    Coupon zeroCouponConverted = zeroCouponInterpolated.toDerivative(pricingDate, priceIndexTS, CURVE_NAMES);
    double paymentTime = ACT_ACT.getDayCountFraction(pricingDate, PAYMENT_DATE);
    double endIndex = 128.23 + (zeroCouponInterpolated.getReferenceEndDate().getDayOfMonth() - 1.0)
        / (zeroCouponInterpolated.getReferenceEndDate().getMonthOfYear().getLastDayOfMonth(zeroCouponInterpolated.getReferenceEndDate().isLeapYear())) * (128.43 - 128.23);
    CouponFixed zeroCoupon = new CouponFixed(CUR, paymentTime, DISCOUNTING_CURVE_NAME, 1.0, NOTIONAL, endIndex / INDEX_APRIL_2008 - 1.0);
    assertEquals("Inflation zero-coupon: toDerivative", zeroCoupon, zeroCouponConverted);
  }

  @Test
  public void toDerivativesInterpolatedOneKnown() {
    final ZonedDateTime pricingDate = DateUtil.getUTCDate(2018, 6, 25);
    final DoubleTimeSeries<ZonedDateTime> priceIndexTS = new ArrayZonedDateTimeDoubleTimeSeries(new ZonedDateTime[] {DateUtil.getUTCDate(2018, 4, 1), DateUtil.getUTCDate(2018, 5, 1)}, new double[] {
        128.03, 128.23});
    CouponInflationZeroCouponDefinition zeroCouponInterpolated = CouponInflationZeroCouponDefinition.from(START_DATE, PAYMENT_DATE, NOTIONAL, PRICE_INDEX, INDEX_APRIL_2008, MONTH_LAG, false);
    Coupon zeroCouponConverted = zeroCouponInterpolated.toDerivative(pricingDate, priceIndexTS, CURVE_NAMES);
    double paymentTime = ACT_ACT.getDayCountFraction(pricingDate, PAYMENT_DATE);
    final double referenceEndTime = -ACT_ACT.getDayCountFraction(zeroCouponInterpolated.getReferenceEndDate(), pricingDate);
    final double fixingTime = ACT_ACT.getDayCountFraction(pricingDate, zeroCouponInterpolated.getFixingEndDate());
    CouponInflationZeroCoupon zeroCoupon = new CouponInflationZeroCoupon(CUR, paymentTime, DISCOUNTING_CURVE_NAME, 1.0, NOTIONAL, PRICE_INDEX, INDEX_APRIL_2008, referenceEndTime, fixingTime,
        PRICE_INDEX_CURVE_NAME);
    assertEquals("Inflation zero-coupon: toDerivative", zeroCoupon, zeroCouponConverted);
  }

  @Test
  public void toDerivativesInterpolatedShouldBeKnown() {
    final ZonedDateTime pricingDate = DateUtil.getUTCDate(2018, 7, 25);
    final DoubleTimeSeries<ZonedDateTime> priceIndexTS = new ArrayZonedDateTimeDoubleTimeSeries(new ZonedDateTime[] {DateUtil.getUTCDate(2018, 4, 1), DateUtil.getUTCDate(2018, 5, 1)}, new double[] {
        128.03, 128.23});
    CouponInflationZeroCouponDefinition zeroCouponInterpolated = CouponInflationZeroCouponDefinition.from(START_DATE, PAYMENT_DATE, NOTIONAL, PRICE_INDEX, INDEX_APRIL_2008, MONTH_LAG, false);
    Coupon zeroCouponConverted = zeroCouponInterpolated.toDerivative(pricingDate, priceIndexTS, CURVE_NAMES);
    double paymentTime = ACT_ACT.getDayCountFraction(pricingDate, PAYMENT_DATE);
    final double referenceEndTime = -ACT_ACT.getDayCountFraction(zeroCouponInterpolated.getReferenceEndDate(), pricingDate);
    final double fixingTime = 0.0;
    CouponInflationZeroCoupon zeroCoupon = new CouponInflationZeroCoupon(CUR, paymentTime, DISCOUNTING_CURVE_NAME, 1.0, NOTIONAL, PRICE_INDEX, INDEX_APRIL_2008, referenceEndTime, fixingTime,
        PRICE_INDEX_CURVE_NAME);
    assertEquals("Inflation zero-coupon: toDerivative", zeroCoupon, zeroCouponConverted);
  }

}
