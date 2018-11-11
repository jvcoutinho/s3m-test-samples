/**
 * Copyright (C) 2011 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.financial.interestrate.inflation.derivatives;

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
import com.opengamma.financial.schedule.ScheduleCalculator;
import com.opengamma.util.money.Currency;
import com.opengamma.util.time.DateUtil;

/**
 * Tests the zero-coupon inflation constructors.
 */
public class CouponInflationZeroCouponTest {
  private static final String NAME = "Euro HICP x";
  private static final Currency CUR = Currency.EUR;
  private static final Currency REGION = Currency.EUR;
  private static final PriceIndex PRICE_INDEX = new PriceIndex(NAME, CUR, REGION);
  private static final Calendar CALENDAR = new MondayToFridayCalendar("A");
  private static final BusinessDayConvention BUSINESS_DAY = BusinessDayConventionFactory.INSTANCE.getBusinessDayConvention("Modified Following");
  private static final ZonedDateTime START_DATE = DateUtil.getUTCDate(2008, 8, 18);
  private static final Period COUPON_TENOR = Period.ofYears(10);
  private static final ZonedDateTime PAYMENT_DATE = ScheduleCalculator.getAdjustedDate(START_DATE, BUSINESS_DAY, CALENDAR, COUPON_TENOR);
  private static final double NOTIONAL = 98765432;
  private static final int MONTH_LAG = 3;
  private static final double INDEX_APRIL_2008 = 108.23; // 3 m before Aug: May / 1 May index = May index: 108.23
  private static final ZonedDateTime REFERENCE_END_DATE = PAYMENT_DATE.minusMonths(MONTH_LAG).withDayOfMonth(1);
  private static final ZonedDateTime FIXING_DATE = REFERENCE_END_DATE.plusMonths(1).withDayOfMonth(1).plusWeeks(2);
  private static final DayCount ACT_ACT = DayCountFactory.INSTANCE.getDayCount("Actual/Actual ISDA");
  private static final ZonedDateTime REFERENCE_DATE = DateUtil.getUTCDate(2011, 7, 29);
  private static final double PAYMENT_TIME = ACT_ACT.getDayCountFraction(REFERENCE_DATE, PAYMENT_DATE);
  private static final double REFERENCE_END_TIME = ACT_ACT.getDayCountFraction(REFERENCE_DATE, REFERENCE_END_DATE);
  private static final double FIXING_TIME = ACT_ACT.getDayCountFraction(REFERENCE_DATE, FIXING_DATE);
  private static final String DISCOUNTING_CURVE_NAME = "Discounting";
  private static final String PRICE_INDEX_CURVE_NAME = "Price index";
  private static final CouponInflationZeroCoupon ZERO_COUPON = new CouponInflationZeroCoupon(CUR, PAYMENT_TIME, DISCOUNTING_CURVE_NAME, 1.0, NOTIONAL, PRICE_INDEX, INDEX_APRIL_2008,
      REFERENCE_END_TIME, FIXING_TIME, PRICE_INDEX_CURVE_NAME);

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNullIndex() {
    new CouponInflationZeroCoupon(CUR, PAYMENT_TIME, DISCOUNTING_CURVE_NAME, 1.0, NOTIONAL, null, INDEX_APRIL_2008, REFERENCE_END_TIME, FIXING_TIME, PRICE_INDEX_CURVE_NAME);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNullIndexName() {
    new CouponInflationZeroCoupon(CUR, PAYMENT_TIME, DISCOUNTING_CURVE_NAME, 1.0, NOTIONAL, PRICE_INDEX, INDEX_APRIL_2008, REFERENCE_END_TIME, FIXING_TIME, null);
  }

  @Test
  /**
   * Tests the class getter.
   */
  public void getter() {
    assertEquals("Inflation Zero-coupon: getter", PRICE_INDEX, ZERO_COUPON.getPriceIndex());
    assertEquals("Inflation Zero-coupon: getter", INDEX_APRIL_2008, ZERO_COUPON.getIndexStartValue());
    assertEquals("Inflation Zero-coupon: getter", REFERENCE_END_TIME, ZERO_COUPON.getReferenceEndTime());
    assertEquals("Inflation Zero-coupon: getter", FIXING_TIME, ZERO_COUPON.getFixingEndTime());
    assertEquals("Inflation Zero-coupon: getter", PRICE_INDEX_CURVE_NAME, ZERO_COUPON.getPriceIndexCurveName());
  }

  @Test
  /**
   * Tests the equal and hash-code methods.
   */
  public void equalHash() {
    assertEquals(ZERO_COUPON, ZERO_COUPON);
    CouponInflationZeroCoupon couponDuplicate = new CouponInflationZeroCoupon(CUR, PAYMENT_TIME, DISCOUNTING_CURVE_NAME, 1.0, NOTIONAL, PRICE_INDEX, INDEX_APRIL_2008, REFERENCE_END_TIME, FIXING_TIME,
        PRICE_INDEX_CURVE_NAME);
    assertEquals(ZERO_COUPON, couponDuplicate);
    assertEquals(ZERO_COUPON.hashCode(), couponDuplicate.hashCode());
    CouponInflationZeroCoupon modified;
    modified = new CouponInflationZeroCoupon(CUR, PAYMENT_TIME, DISCOUNTING_CURVE_NAME, 1.0, NOTIONAL, PRICE_INDEX, INDEX_APRIL_2008 + 0.1, REFERENCE_END_TIME, FIXING_TIME, PRICE_INDEX_CURVE_NAME);
    assertFalse(ZERO_COUPON.equals(modified));
    modified = new CouponInflationZeroCoupon(CUR, PAYMENT_TIME, DISCOUNTING_CURVE_NAME, 1.0, NOTIONAL, PRICE_INDEX, INDEX_APRIL_2008, REFERENCE_END_TIME + 0.1, FIXING_TIME, PRICE_INDEX_CURVE_NAME);
    assertFalse(ZERO_COUPON.equals(modified));
    modified = new CouponInflationZeroCoupon(CUR, PAYMENT_TIME, DISCOUNTING_CURVE_NAME, 1.0, NOTIONAL, PRICE_INDEX, INDEX_APRIL_2008, REFERENCE_END_TIME, FIXING_TIME + 0.1, PRICE_INDEX_CURVE_NAME);
    assertFalse(ZERO_COUPON.equals(modified));
    modified = new CouponInflationZeroCoupon(CUR, PAYMENT_TIME, DISCOUNTING_CURVE_NAME, 1.0, NOTIONAL, PRICE_INDEX, INDEX_APRIL_2008, REFERENCE_END_TIME, FIXING_TIME, "modified");
    assertFalse(ZERO_COUPON.equals(modified));
  }

}
