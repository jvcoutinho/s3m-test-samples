/**
 * Copyright (C) 2011 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.analytics.financial.interestrate.inflation.derivative;

import java.util.Arrays;

import com.opengamma.analytics.financial.instrument.index.IndexPrice;
import com.opengamma.analytics.financial.interestrate.InstrumentDerivativeVisitor;
import com.opengamma.analytics.financial.interestrate.market.description.IMarketBundle;
import com.opengamma.util.money.Currency;

/**
 * Class describing an zero-coupon inflation coupon. 
 * The start index value is known when the coupon is traded/issued.
 * The index for a given month is given in the yield curve and in the time series on the first of the month.
 */
public class CouponInflationZeroCouponInterpolation extends CouponInflation {

  /**
   * The index value at the start of the coupon.
   */
  private final double _indexStartValue;
  /**
   * The reference times for the index at the coupon end.  Two months are required for the interpolation. 
   * There is usually a difference of two or three month between the reference date and the payment date.
   * The time can be negative (when the price index for the current and last month is not yet published).
   */
  private final double[] _referenceEndTime;
  /**
   * The weight on the first month index in the interpolation.
   */
  private final double _weight;
  /**
   * The time on which the end index is expected to be known. The index is usually known two week after the end of the reference month. 
   * The date is only an "expected date" as the index publication could be delayed for different reasons. The date should not be enforced to strictly in pricing and instrument creation.
   */
  private final double _fixingEndTime;
  /**
   * Flag indicating if the notional is paid (true) or not (false) at the end of the period.
   */
  private final boolean _payNotional;

  /**
   * Inflation zero-coupon constructor.
   * @param currency The coupon currency.
   * @param paymentTime The time to payment.
   * @param fundingCurveName The discounting curve name.
   * @param paymentYearFraction Accrual factor of the accrual period.
   * @param notional Coupon notional.
   * @param priceIndex The price index associated to the coupon.
   * @param indexStartValue The index value at the start of the coupon.
   * @param referenceEndTime The reference time for the index at the coupon end.
   * @param weight The weight on the first month index in the interpolation.
   * @param fixingEndTime The time on which the end index is expected to be known.
   * @param payNotional Flag indicating if the notional is paid (true) or not (false).
   */
  public CouponInflationZeroCouponInterpolation(Currency currency, double paymentTime, String fundingCurveName, double paymentYearFraction, double notional, IndexPrice priceIndex,
      double indexStartValue, double[] referenceEndTime, double weight, double fixingEndTime, boolean payNotional) {
    super(currency, paymentTime, fundingCurveName, paymentYearFraction, notional, priceIndex);
    this._indexStartValue = indexStartValue;
    this._referenceEndTime = referenceEndTime;
    this._fixingEndTime = fixingEndTime;
    _weight = weight;
    _payNotional = payNotional;
  }

  /**
   * Gets the index value at the start of the coupon.
   * @return The index value.
   */
  public double getIndexStartValue() {
    return _indexStartValue;
  }

  /**
   * Gets the reference time for the index at the coupon end.
   * @return The reference time.
   */
  public double[] getReferenceEndTime() {
    return _referenceEndTime;
  }

  /**
   * Gets the time on which the end index is expected to be known.
   * @return The time on which the end index is expected to be known.
   */
  public double getFixingEndTime() {
    return _fixingEndTime;
  }

  /**
   * Gets the weight on the first month index in the interpolation.
   * @return The weight.
   */
  public double getWeight() {
    return _weight;
  }

  /**
   * Gets the pay notional flag.
   * @return The flag.
   */
  public boolean payNotional() {
    return _payNotional;
  }

  @Override
  public CouponInflationZeroCouponInterpolation withNotional(double notional) {
    return new CouponInflationZeroCouponInterpolation(getCurrency(), getPaymentTime(), getFundingCurveName(), getPaymentYearFraction(), notional, getPriceIndex(), _indexStartValue, _referenceEndTime,
        _weight, _fixingEndTime, _payNotional);
  }

  @Override
  public double estimatedIndex(IMarketBundle market) {
    double estimatedIndexMonth0 = market.getPriceIndex(getPriceIndex(), _referenceEndTime[0]);
    double estimatedIndexMonth1 = market.getPriceIndex(getPriceIndex(), _referenceEndTime[1]);
    double estimatedIndex = _weight * estimatedIndexMonth0 + (1 - _weight) * estimatedIndexMonth1;
    return estimatedIndex;
  }

  @Override
  public <S, T> T accept(InstrumentDerivativeVisitor<S, T> visitor, S data) {
    return visitor.visitCouponInflationZeroCouponInterpolation(this, data);
  }

  @Override
  public <T> T accept(InstrumentDerivativeVisitor<?, T> visitor) {
    return visitor.visitCouponInflationZeroCouponInterpolation(this);
  }

  @Override
  public String toString() {
    return super.toString() + ", reference=[" + _referenceEndTime[0] + ", " + _referenceEndTime[1] + ", fixing=" + _fixingEndTime;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    long temp;
    temp = Double.doubleToLongBits(_fixingEndTime);
    result = prime * result + (int) (temp ^ (temp >>> 32));
    temp = Double.doubleToLongBits(_indexStartValue);
    result = prime * result + (int) (temp ^ (temp >>> 32));
    result = prime * result + Arrays.hashCode(_referenceEndTime);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    CouponInflationZeroCouponInterpolation other = (CouponInflationZeroCouponInterpolation) obj;
    if (Double.doubleToLongBits(_fixingEndTime) != Double.doubleToLongBits(other._fixingEndTime)) {
      return false;
    }
    if (Double.doubleToLongBits(_indexStartValue) != Double.doubleToLongBits(other._indexStartValue)) {
      return false;
    }
    if (!Arrays.equals(_referenceEndTime, other._referenceEndTime)) {
      return false;
    }
    return true;
  }

}
