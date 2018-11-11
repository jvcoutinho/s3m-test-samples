/**
 * Copyright (C) 2011 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.analytics.financial.interestrate;

import java.util.TreeMap;

import org.apache.commons.lang.Validate;

import com.opengamma.analytics.financial.interestrate.annuity.derivative.Annuity;
import com.opengamma.analytics.financial.interestrate.annuity.derivative.AnnuityCouponFixed;
import com.opengamma.analytics.financial.interestrate.annuity.derivative.AnnuityCouponIbor;
import com.opengamma.analytics.financial.interestrate.annuity.derivative.AnnuityPaymentFixed;
import com.opengamma.analytics.financial.interestrate.bond.definition.BondFixedSecurity;
import com.opengamma.analytics.financial.interestrate.payments.derivative.CouponFixed;
import com.opengamma.analytics.financial.interestrate.payments.derivative.CouponIbor;
import com.opengamma.analytics.financial.interestrate.payments.derivative.CouponIborGearing;
import com.opengamma.analytics.financial.interestrate.payments.derivative.CouponIborSpread;
import com.opengamma.analytics.financial.interestrate.payments.derivative.Payment;
import com.opengamma.analytics.financial.interestrate.payments.derivative.PaymentFixed;
import com.opengamma.analytics.financial.interestrate.swap.derivative.FixedFloatSwap;
import com.opengamma.analytics.financial.interestrate.swap.derivative.Swap;
import com.opengamma.analytics.financial.interestrate.swap.derivative.SwapFixedCoupon;
import com.opengamma.analytics.financial.model.interestrate.curve.YieldAndDiscountCurve;
import com.opengamma.util.money.Currency;

/**
 * Compute the cash flow equivalent of simple instruments (in single or multi-curve framework).
 * The cash-flow equivalent have at most one payment by time and the times are sorted in ascending order.
 * Reference: Henrard, M. The Irony in the derivatives discounting Part II: the crisis. Wilmott Journal, 2010, 2, 301-316
 */
public class CashFlowEquivalentCalculator extends AbstractInstrumentDerivativeVisitor<YieldCurveBundle, AnnuityPaymentFixed> {

  /**
   * The unique instance of the calculator.
   */
  private static final CashFlowEquivalentCalculator s_instance = new CashFlowEquivalentCalculator();

  /**
   * Gets the calculator instance.
   * @return The calculator.
   */
  public static CashFlowEquivalentCalculator getInstance() {
    return s_instance;
  }

  /**
   * Constructor.
   */
  CashFlowEquivalentCalculator() {
  }

  @Override
  public AnnuityPaymentFixed visit(final InstrumentDerivative derivative, final YieldCurveBundle curves) {
    Validate.notNull(curves);
    Validate.notNull(derivative);
    return derivative.accept(this, curves);
  }

  @Override
  public AnnuityPaymentFixed visitFixedPayment(final PaymentFixed payment, final YieldCurveBundle curves) {
    Validate.notNull(curves);
    Validate.notNull(payment);
    return new AnnuityPaymentFixed(new PaymentFixed[] {payment});
  }

  @Override
  public AnnuityPaymentFixed visitCouponFixed(final CouponFixed coupon, final YieldCurveBundle curves) {
    Validate.notNull(curves);
    Validate.notNull(coupon);
    return new AnnuityPaymentFixed(new PaymentFixed[] {coupon.toPaymentFixed()});
  }

  @Override
  public AnnuityPaymentFixed visitCouponIbor(final CouponIbor payment, final YieldCurveBundle curves) {
    Validate.notNull(curves);
    Validate.notNull(payment);
    final YieldAndDiscountCurve discountingCurve = curves.getCurve(payment.getFundingCurveName());
    final YieldAndDiscountCurve forwardCurve = curves.getCurve(payment.getForwardCurveName());
    double fixingStartTime = payment.getFixingPeriodStartTime();
    double fixingEndTime = payment.getFixingPeriodEndTime();
    double paymentTime = payment.getPaymentTime();
    final double beta = forwardCurve.getDiscountFactor(fixingStartTime) / forwardCurve.getDiscountFactor(fixingEndTime) * discountingCurve.getDiscountFactor(paymentTime)
        / discountingCurve.getDiscountFactor(fixingStartTime);
    PaymentFixed paymentStart = new PaymentFixed(payment.getCurrency(), fixingStartTime, beta * payment.getNotional() * payment.getPaymentYearFraction() / payment.getFixingAccrualFactor(),
        payment.getFundingCurveName());
    PaymentFixed paymentEnd = new PaymentFixed(payment.getCurrency(), paymentTime, -payment.getNotional() * payment.getPaymentYearFraction() / payment.getFixingAccrualFactor(),
        payment.getFundingCurveName());
    return new AnnuityPaymentFixed(new PaymentFixed[] {paymentStart, paymentEnd});
  }

  @Override
  public AnnuityPaymentFixed visitCouponIborSpread(final CouponIborSpread payment, final YieldCurveBundle curves) {
    Validate.notNull(curves);
    Validate.notNull(payment);
    final YieldAndDiscountCurve discountingCurve = curves.getCurve(payment.getFundingCurveName());
    final YieldAndDiscountCurve forwardCurve = curves.getCurve(payment.getForwardCurveName());
    double fixingStartTime = payment.getFixingPeriodStartTime();
    double fixingEndTime = payment.getFixingPeriodEndTime();
    double paymentTime = payment.getPaymentTime();
    final double beta = forwardCurve.getDiscountFactor(fixingStartTime) / forwardCurve.getDiscountFactor(fixingEndTime) * discountingCurve.getDiscountFactor(paymentTime)
        / discountingCurve.getDiscountFactor(fixingStartTime);
    PaymentFixed paymentStart = new PaymentFixed(payment.getCurrency(), fixingStartTime, beta * payment.getNotional() * payment.getPaymentYearFraction() / payment.getFixingYearFraction(),
        payment.getFundingCurveName());
    PaymentFixed paymentEnd = new PaymentFixed(payment.getCurrency(), paymentTime, (-payment.getNotional() + payment.getSpreadAmount()) * payment.getPaymentYearFraction()
        / payment.getFixingYearFraction(), payment.getFundingCurveName());
    return new AnnuityPaymentFixed(new PaymentFixed[] {paymentStart, paymentEnd});
  }

  @Override
  public AnnuityPaymentFixed visitCouponIborGearing(final CouponIborGearing payment, final YieldCurveBundle curves) {
    Validate.notNull(curves);
    Validate.notNull(payment);
    final YieldAndDiscountCurve discountingCurve = curves.getCurve(payment.getFundingCurveName());
    final YieldAndDiscountCurve forwardCurve = curves.getCurve(payment.getForwardCurveName());
    double fixingStartTime = payment.getFixingPeriodStartTime();
    double fixingEndTime = payment.getFixingPeriodEndTime();
    double paymentTime = payment.getPaymentTime();
    final double beta = forwardCurve.getDiscountFactor(fixingStartTime) / forwardCurve.getDiscountFactor(fixingEndTime) * discountingCurve.getDiscountFactor(paymentTime)
        / discountingCurve.getDiscountFactor(fixingStartTime);
    PaymentFixed paymentStart = new PaymentFixed(payment.getCurrency(), fixingStartTime, payment.getFactor() * beta * payment.getNotional() * payment.getPaymentYearFraction()
        / payment.getFixingAccrualFactor(), payment.getFundingCurveName());
    PaymentFixed paymentEnd = new PaymentFixed(payment.getCurrency(), paymentTime, (-payment.getFactor() / payment.getFixingAccrualFactor() + payment.getSpread()) * payment.getPaymentYearFraction()
        * payment.getNotional(), payment.getFundingCurveName());
    return new AnnuityPaymentFixed(new PaymentFixed[] {paymentStart, paymentEnd});
  }

  @Override
  public AnnuityPaymentFixed visitGenericAnnuity(final Annuity<? extends Payment> annuity, final YieldCurveBundle curves) {
    Validate.notNull(curves);
    Validate.notNull(annuity);
    TreeMap<Double, Double> flow = new TreeMap<Double, Double>();
    Currency ccy = annuity.getCurrency();
    for (final Payment p : annuity.getPayments()) {
      AnnuityPaymentFixed cfe = visit(p, curves);
      for (int loopcf = 0; loopcf < cfe.getNumberOfPayments(); loopcf++) {
        addcf(flow, cfe.getNthPayment(loopcf).getPaymentTime(), cfe.getNthPayment(loopcf).getAmount());
      }
    }
    PaymentFixed[] agregatedCfe = new PaymentFixed[flow.size()];
    int loopcf = 0;
    for (double time : flow.keySet()) {
      agregatedCfe[loopcf++] = new PaymentFixed(ccy, time, flow.get(time), annuity.getDiscountCurve());
    }
    return new AnnuityPaymentFixed(agregatedCfe);
  }

  @Override
  public AnnuityPaymentFixed visitFixedCouponAnnuity(final AnnuityCouponFixed annuity, final YieldCurveBundle curves) {
    return visitGenericAnnuity(annuity, curves);
  }

  @Override
  public AnnuityPaymentFixed visitForwardLiborAnnuity(final AnnuityCouponIbor annuity, final YieldCurveBundle curves) {
    return visitGenericAnnuity(annuity, curves);
  }

  @Override
  public AnnuityPaymentFixed visitSwap(final Swap<?, ?> swap, final YieldCurveBundle curves) {
    Validate.notNull(curves);
    Validate.notNull(swap);
    Currency ccy = swap.getFirstLeg().getCurrency();
    Validate.isTrue(ccy.equals(swap.getSecondLeg().getCurrency()), "Cash flow equivalent available only for single currency swaps.");
    TreeMap<Double, Double> flow = new TreeMap<Double, Double>();
    AnnuityPaymentFixed cfeLeg1 = visit(swap.getFirstLeg(), curves);
    AnnuityPaymentFixed cfeLeg2 = visit(swap.getSecondLeg(), curves);
    for (final PaymentFixed p : cfeLeg1.getPayments()) {
      flow.put(p.getPaymentTime(), p.getAmount());
    }
    for (final PaymentFixed p : cfeLeg2.getPayments()) {
      addcf(flow, p.getPaymentTime(), p.getAmount());
    }
    PaymentFixed[] agregatedCfe = new PaymentFixed[flow.size()];
    int loopcf = 0;
    for (double time : flow.keySet()) {
      agregatedCfe[loopcf++] = new PaymentFixed(ccy, time, flow.get(time), cfeLeg1.getDiscountCurve());
    }
    return new AnnuityPaymentFixed(agregatedCfe);
  }

  @Override
  public AnnuityPaymentFixed visitFixedCouponSwap(final SwapFixedCoupon<?> swap, final YieldCurveBundle curves) {
    return visitSwap(swap, curves);
  }

  @Override
  public AnnuityPaymentFixed visitFixedFloatSwap(final FixedFloatSwap swap, final YieldCurveBundle curves) {
    return visitSwap(swap, curves);
  }

  @Override
  public AnnuityPaymentFixed visitBondFixedSecurity(final BondFixedSecurity bond, final YieldCurveBundle curves) {
    Validate.notNull(curves);
    Validate.notNull(bond);
    Currency ccy = bond.getCurrency();
    TreeMap<Double, Double> flow = new TreeMap<Double, Double>();
    AnnuityPaymentFixed cfeNom = visit(bond.getNominal(), curves);
    AnnuityPaymentFixed cfeCpn = visit(bond.getCoupon(), curves);
    for (final PaymentFixed p : cfeNom.getPayments()) {
      flow.put(p.getPaymentTime(), p.getAmount());
    }
    for (final PaymentFixed p : cfeCpn.getPayments()) {
      addcf(flow, p.getPaymentTime(), p.getAmount());
    }
    PaymentFixed[] agregatedCfe = new PaymentFixed[flow.size()];
    int loopcf = 0;
    for (double time : flow.keySet()) {
      agregatedCfe[loopcf++] = new PaymentFixed(ccy, time, flow.get(time), cfeCpn.getDiscountCurve());
    }
    return new AnnuityPaymentFixed(agregatedCfe);

  }

  /**
   * Add a cash flow amount at a given time in the flow map. If the time is present, the amount is added; if the time is not present a new entry is created.
   * @param flow The map describing the cash flows.
   * @param time The time of the flow to add.
   * @param amount The amount of the flow to add.
   */
  private void addcf(TreeMap<Double, Double> flow, double time, double amount) {
    if (flow.containsKey(time)) {
      flow.put(time, flow.get(time) + amount);
    } else {
      flow.put(time, amount);
    }
  }
}
