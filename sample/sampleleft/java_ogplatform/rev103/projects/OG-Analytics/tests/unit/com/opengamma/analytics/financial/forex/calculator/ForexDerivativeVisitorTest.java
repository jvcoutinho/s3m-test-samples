/**
 * Copyright (C) 2011 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.analytics.financial.forex.calculator;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

import org.testng.annotations.Test;

import com.opengamma.analytics.financial.credit.cds.ISDACDSDerivative;
import com.opengamma.analytics.financial.forex.derivative.Forex;
import com.opengamma.analytics.financial.forex.derivative.ForexNonDeliverableForward;
import com.opengamma.analytics.financial.forex.derivative.ForexNonDeliverableOption;
import com.opengamma.analytics.financial.forex.derivative.ForexOptionDigital;
import com.opengamma.analytics.financial.forex.derivative.ForexOptionSingleBarrier;
import com.opengamma.analytics.financial.forex.derivative.ForexOptionVanilla;
import com.opengamma.analytics.financial.forex.derivative.ForexSwap;
import com.opengamma.analytics.financial.interestrate.AbstractInstrumentDerivativeVisitor;
import com.opengamma.analytics.financial.interestrate.InstrumentDerivative;
import com.opengamma.analytics.financial.interestrate.InstrumentDerivativeVisitor;
import com.opengamma.analytics.financial.interestrate.annuity.derivative.Annuity;
import com.opengamma.analytics.financial.interestrate.annuity.derivative.AnnuityCouponFixed;
import com.opengamma.analytics.financial.interestrate.annuity.derivative.AnnuityCouponIbor;
import com.opengamma.analytics.financial.interestrate.annuity.derivative.AnnuityCouponIborRatchet;
import com.opengamma.analytics.financial.interestrate.annuity.derivative.AnnuityCouponIborSpread;
import com.opengamma.analytics.financial.interestrate.bond.definition.BillSecurity;
import com.opengamma.analytics.financial.interestrate.bond.definition.BillTransaction;
import com.opengamma.analytics.financial.interestrate.bond.definition.BondCapitalIndexedSecurity;
import com.opengamma.analytics.financial.interestrate.bond.definition.BondCapitalIndexedTransaction;
import com.opengamma.analytics.financial.interestrate.bond.definition.BondFixedSecurity;
import com.opengamma.analytics.financial.interestrate.bond.definition.BondFixedTransaction;
import com.opengamma.analytics.financial.interestrate.bond.definition.BondIborSecurity;
import com.opengamma.analytics.financial.interestrate.bond.definition.BondIborTransaction;
import com.opengamma.analytics.financial.interestrate.cash.derivative.Cash;
import com.opengamma.analytics.financial.interestrate.cash.derivative.DepositCounterpart;
import com.opengamma.analytics.financial.interestrate.cash.derivative.DepositIbor;
import com.opengamma.analytics.financial.interestrate.cash.derivative.DepositZero;
import com.opengamma.analytics.financial.interestrate.fra.ForwardRateAgreement;
import com.opengamma.analytics.financial.interestrate.future.derivative.BondFuture;
import com.opengamma.analytics.financial.interestrate.future.derivative.BondFutureOptionPremiumSecurity;
import com.opengamma.analytics.financial.interestrate.future.derivative.BondFutureOptionPremiumTransaction;
import com.opengamma.analytics.financial.interestrate.future.derivative.FederalFundsFutureSecurity;
import com.opengamma.analytics.financial.interestrate.future.derivative.FederalFundsFutureTransaction;
import com.opengamma.analytics.financial.interestrate.future.derivative.InterestRateFuture;
import com.opengamma.analytics.financial.interestrate.future.derivative.InterestRateFutureOptionMarginSecurity;
import com.opengamma.analytics.financial.interestrate.future.derivative.InterestRateFutureOptionMarginTransaction;
import com.opengamma.analytics.financial.interestrate.future.derivative.InterestRateFutureOptionPremiumSecurity;
import com.opengamma.analytics.financial.interestrate.future.derivative.InterestRateFutureOptionPremiumTransaction;
import com.opengamma.analytics.financial.interestrate.inflation.derivative.CouponInflationZeroCouponInterpolation;
import com.opengamma.analytics.financial.interestrate.inflation.derivative.CouponInflationZeroCouponInterpolationGearing;
import com.opengamma.analytics.financial.interestrate.inflation.derivative.CouponInflationZeroCouponMonthly;
import com.opengamma.analytics.financial.interestrate.inflation.derivative.CouponInflationZeroCouponMonthlyGearing;
import com.opengamma.analytics.financial.interestrate.payments.ForexForward;
import com.opengamma.analytics.financial.interestrate.payments.derivative.CapFloorCMS;
import com.opengamma.analytics.financial.interestrate.payments.derivative.CapFloorCMSSpread;
import com.opengamma.analytics.financial.interestrate.payments.derivative.CapFloorIbor;
import com.opengamma.analytics.financial.interestrate.payments.derivative.CouponCMS;
import com.opengamma.analytics.financial.interestrate.payments.derivative.CouponFixed;
import com.opengamma.analytics.financial.interestrate.payments.derivative.CouponIbor;
import com.opengamma.analytics.financial.interestrate.payments.derivative.CouponIborCompounded;
import com.opengamma.analytics.financial.interestrate.payments.derivative.CouponIborGearing;
import com.opengamma.analytics.financial.interestrate.payments.derivative.CouponIborSpread;
import com.opengamma.analytics.financial.interestrate.payments.derivative.CouponOIS;
import com.opengamma.analytics.financial.interestrate.payments.derivative.Payment;
import com.opengamma.analytics.financial.interestrate.payments.derivative.PaymentFixed;
import com.opengamma.analytics.financial.interestrate.swap.derivative.CrossCurrencySwap;
import com.opengamma.analytics.financial.interestrate.swap.derivative.FixedFloatSwap;
import com.opengamma.analytics.financial.interestrate.swap.derivative.FloatingRateNote;
import com.opengamma.analytics.financial.interestrate.swap.derivative.Swap;
import com.opengamma.analytics.financial.interestrate.swap.derivative.SwapFixedCoupon;
import com.opengamma.analytics.financial.interestrate.swap.derivative.TenorSwap;
import com.opengamma.analytics.financial.interestrate.swaption.derivative.SwaptionBermudaFixedIbor;
import com.opengamma.analytics.financial.interestrate.swaption.derivative.SwaptionCashFixedIbor;
import com.opengamma.analytics.financial.interestrate.swaption.derivative.SwaptionPhysicalFixedIbor;

/**
 * Tests the visitor of Forex derivatives.
 */
public class ForexDerivativeVisitorTest {

  private static final Forex FX = ForexInstrumentsDescriptionDataSet.createForex();
  private static final ForexSwap FX_SWAP = ForexInstrumentsDescriptionDataSet.createForexSwap();
  private static final ForexOptionVanilla FX_OPTION = ForexInstrumentsDescriptionDataSet.createForexOptionVanilla();
  private static final ForexOptionSingleBarrier FX_OPTION_SINGLE_BARRIER = ForexInstrumentsDescriptionDataSet.createForexOptionSingleBarrier();
  private static final ForexNonDeliverableForward NDF = ForexInstrumentsDescriptionDataSet.createForexNonDeliverableForward();
  private static final ForexNonDeliverableOption NDO = ForexInstrumentsDescriptionDataSet.createForexNonDeliverableOption();
  private static final ForexOptionDigital FX_OPTION_DIGITAL = ForexInstrumentsDescriptionDataSet.createForexOptionDigital();

  @SuppressWarnings("synthetic-access")
  private static final MyVisitor<Object, String> VISITOR = new MyVisitor<Object, String>();

  @SuppressWarnings("synthetic-access")
  private static final MyAbstractVisitor<Object, String> VISITOR_ABSTRACT = new MyAbstractVisitor<Object, String>();

  @Test
  public void testVisitor() {
    final Object o = "G";
    assertEquals(FX.accept(VISITOR), "Forex1");
    assertEquals(FX.accept(VISITOR, o), "Forex2");
    assertEquals(FX_SWAP.accept(VISITOR), "ForexSwap1");
    assertEquals(FX_SWAP.accept(VISITOR, o), "ForexSwap2");
    assertEquals(FX_OPTION.accept(VISITOR), "ForexOptionVanilla1");
    assertEquals(FX_OPTION.accept(VISITOR, o), "ForexOptionVanilla2");
    assertEquals(FX_OPTION_SINGLE_BARRIER.accept(VISITOR), "ForexOptionSingleBarrier1");
    assertEquals(FX_OPTION_SINGLE_BARRIER.accept(VISITOR, o), "ForexOptionSingleBarrier2");
    assertEquals(NDF.accept(VISITOR), "ForexNonDeliverableForward1");
    assertEquals(NDF.accept(VISITOR, o), "ForexNonDeliverableForward2");
    assertEquals(NDO.accept(VISITOR), "ForexNonDeliverableOption1");
    assertEquals(NDO.accept(VISITOR, o), "ForexNonDeliverableOption2");
    assertEquals(FX_OPTION_DIGITAL.accept(VISITOR), "ForexOptionDigital1");
    assertEquals(FX_OPTION_DIGITAL.accept(VISITOR, o), "ForexOptionDigital2");
  }

  @Test
  public void testAbstractVisitorException() {
    final Object o = "G";
    testException(FX);
    testException(FX, o);
    testException(FX_SWAP);
    testException(FX_SWAP, o);
    testException(FX_OPTION);
    testException(FX_OPTION, o);
    testException(FX_OPTION_SINGLE_BARRIER);
    testException(FX_OPTION_SINGLE_BARRIER, o);
    testException(NDF);
    testException(NDF, o);
    testException(NDO);
    testException(NDO, o);
    testException(FX_OPTION_DIGITAL);
    testException(FX_OPTION_DIGITAL, o);
    final InstrumentDerivative[] forexArray = new InstrumentDerivative[] {FX, FX_SWAP };
    try {
      VISITOR_ABSTRACT.visit(forexArray[0]);
      assertTrue(false);
    } catch (final UnsupportedOperationException e) {
      assertTrue(true);
    } catch (final Exception e) {
      assertTrue(false);
    }
    try {
      VISITOR_ABSTRACT.visit(forexArray);
      assertTrue(false);
    } catch (final UnsupportedOperationException e) {
      assertTrue(true);
    } catch (final Exception e) {
      assertTrue(false);
    }
    try {
      VISITOR_ABSTRACT.visit(forexArray, o);
      assertTrue(false);
    } catch (final UnsupportedOperationException e) {
      assertTrue(true);
    } catch (final Exception e) {
      assertTrue(false);
    }
  }

  private void testException(final InstrumentDerivative fx) {
    try {
      fx.accept(VISITOR_ABSTRACT);
      assertTrue(false);
    } catch (final UnsupportedOperationException e) {
      assertTrue(true);
    } catch (final Exception e) {
      assertTrue(false);
    }
  }

  private void testException(final InstrumentDerivative fx, final Object o) {
    try {
      fx.accept(VISITOR_ABSTRACT, o);
      assertTrue(false);
    } catch (final UnsupportedOperationException e) {
      assertTrue(true);
    } catch (final Exception e) {
      assertTrue(false);
    }
  }

  private static class MyVisitor<T, U> implements InstrumentDerivativeVisitor<T, String> {

    @Override
    public String visit(final InstrumentDerivative derivative, final T data) {
      return null;
    }

    @Override
    public String visit(final InstrumentDerivative derivative) {
      return null;
    }

    @Override
    public String[] visit(final InstrumentDerivative[] derivative, final T data) {
      return null;
    }

    @Override
    public String[] visit(final InstrumentDerivative[] derivative) {
      return null;
    }

    @Override
    public String visitForex(final Forex derivative, final T data) {
      return "Forex2";
    }

    @Override
    public String visitForex(final Forex derivative) {
      return "Forex1";
    }

    @Override
    public String visitForexSwap(final ForexSwap derivative, final T data) {
      return "ForexSwap2";
    }

    @Override
    public String visitForexSwap(final ForexSwap derivative) {
      return "ForexSwap1";
    }

    @Override
    public String visitForexOptionVanilla(final ForexOptionVanilla derivative, final T data) {
      return "ForexOptionVanilla2";
    }

    @Override
    public String visitForexOptionVanilla(final ForexOptionVanilla derivative) {
      return "ForexOptionVanilla1";
    }

    @Override
    public String visitForexOptionSingleBarrier(final ForexOptionSingleBarrier derivative, final T data) {
      return "ForexOptionSingleBarrier2";
    }

    @Override
    public String visitForexOptionSingleBarrier(final ForexOptionSingleBarrier derivative) {
      return "ForexOptionSingleBarrier1";
    }

    @Override
    public String visitForexNonDeliverableForward(ForexNonDeliverableForward derivative, T data) {
      return "ForexNonDeliverableForward2";
    }

    @Override
    public String visitForexNonDeliverableForward(ForexNonDeliverableForward derivative) {
      return "ForexNonDeliverableForward1";
    }

    @Override
    public String visitForexNonDeliverableOption(ForexNonDeliverableOption derivative, T data) {
      return "ForexNonDeliverableOption2";
    }

    @Override
    public String visitForexNonDeliverableOption(ForexNonDeliverableOption derivative) {
      return "ForexNonDeliverableOption1";
    }

    @Override
    public String visitBondFixedSecurity(BondFixedSecurity bond, T data) {
      return null;
    }

    @Override
    public String visitBondFixedTransaction(BondFixedTransaction bond, T data) {
      return null;
    }

    @Override
    public String visitBondIborSecurity(BondIborSecurity bond, T data) {
      return null;
    }

    @Override
    public String visitBondIborTransaction(BondIborTransaction bond, T data) {
      return null;
    }

    @Override
    public String visitGenericAnnuity(Annuity<? extends Payment> genericAnnuity, T data) {
      return null;
    }

    @Override
    public String visitFixedCouponAnnuity(AnnuityCouponFixed fixedCouponAnnuity, T data) {
      return null;
    }

    @Override
    public String visitForwardLiborAnnuity(AnnuityCouponIbor forwardLiborAnnuity, T data) {
      return null;
    }

    @Override
    public String visitAnnuityCouponIborRatchet(AnnuityCouponIborRatchet annuity, T data) {
      return null;
    }

    @Override
    public String visitSwap(Swap<?, ?> swap, T data) {
      return null;
    }

    @Override
    public String visitFixedCouponSwap(SwapFixedCoupon<?> swap, T data) {
      return null;
    }

    @Override
    public String visitFixedFloatSwap(FixedFloatSwap swap, T data) {
      return null;
    }

    @Override
    public String visitSwaptionCashFixedIbor(SwaptionCashFixedIbor swaption, T data) {
      return null;
    }

    @Override
    public String visitSwaptionPhysicalFixedIbor(SwaptionPhysicalFixedIbor swaption, T data) {
      return null;
    }

    @Override
    public String visitSwaptionBermudaFixedIbor(SwaptionBermudaFixedIbor swaption, T data) {
      return null;
    }

    @Override
    public String visitTenorSwap(TenorSwap<? extends Payment> tenorSwap, T data) {
      return null;
    }

    @Override
    public String visitFloatingRateNote(FloatingRateNote frn, T data) {
      return null;
    }

    @Override
    public String visitCrossCurrencySwap(CrossCurrencySwap ccs, T data) {
      return null;
    }

    @Override
    public String visitForexForward(ForexForward fx, T data) {
      return null;
    }

    @Override
    public String visitCash(Cash cash, T data) {
      return null;
    }

    @Override
    public String visitBondFuture(BondFuture bondFuture, T data) {
      return null;
    }

    @Override
    public String visitInterestRateFuture(InterestRateFuture future, T data) {
      return null;
    }

    @Override
    public String visitInterestRateFutureOptionPremiumSecurity(InterestRateFutureOptionPremiumSecurity option, T data) {
      return null;
    }

    @Override
    public String visitInterestRateFutureOptionPremiumTransaction(InterestRateFutureOptionPremiumTransaction option, T data) {
      return null;
    }

    @Override
    public String visitInterestRateFutureOptionMarginSecurity(InterestRateFutureOptionMarginSecurity option, T data) {
      return null;
    }

    @Override
    public String visitInterestRateFutureOptionMarginTransaction(InterestRateFutureOptionMarginTransaction option, T data) {
      return null;
    }

    @Override
    public String visitFixedPayment(PaymentFixed payment, T data) {
      return null;
    }

    @Override
    public String visitCouponFixed(CouponFixed payment, T data) {
      return null;
    }

    @Override
    public String visitCouponIborSpread(CouponIborSpread payment, T data) {
      return null;
    }

    @Override
    public String visitCouponIborGearing(CouponIborGearing payment, T data) {
      return null;
    }

    @Override
    public String visitCouponOIS(CouponOIS payment, T data) {
      return null;
    }

    @Override
    public String visitCouponCMS(CouponCMS payment, T data) {
      return null;
    }

    @Override
    public String visitCapFloorIbor(CapFloorIbor payment, T data) {
      return null;
    }

    @Override
    public String visitCapFloorCMS(CapFloorCMS payment, T data) {
      return null;
    }

    @Override
    public String visitCapFloorCMSSpread(CapFloorCMSSpread payment, T data) {
      return null;
    }

    @Override
    public String visitForwardRateAgreement(ForwardRateAgreement fra, T data) {
      return null;
    }

    @Override
    public String visitCouponInflationZeroCouponMonthly(CouponInflationZeroCouponMonthly coupon, T data) {
      return null;
    }

    @Override
    public String visitCouponInflationZeroCouponMonthlyGearing(CouponInflationZeroCouponMonthlyGearing coupon, T data) {
      return null;
    }

    @Override
    public String visitCouponInflationZeroCouponInterpolation(CouponInflationZeroCouponInterpolation coupon, T data) {
      return null;
    }

    @Override
    public String visitCouponInflationZeroCouponInterpolationGearing(CouponInflationZeroCouponInterpolationGearing coupon, T data) {
      return null;
    }

    @Override
    public String visitBondCapitalIndexedSecurity(BondCapitalIndexedSecurity<?> bond, T data) {
      return null;
    }

    @Override
    public String visitBondCapitalIndexedTransaction(BondCapitalIndexedTransaction<?> bond, T data) {
      return null;
    }

    @Override
    public String visitBondFixedSecurity(BondFixedSecurity bond) {
      return null;
    }

    @Override
    public String visitBondFixedTransaction(BondFixedTransaction bond) {
      return null;
    }

    @Override
    public String visitBondIborSecurity(BondIborSecurity bond) {
      return null;
    }

    @Override
    public String visitBondIborTransaction(BondIborTransaction bond) {
      return null;
    }

    @Override
    public String visitGenericAnnuity(Annuity<? extends Payment> genericAnnuity) {
      return null;
    }

    @Override
    public String visitFixedCouponAnnuity(AnnuityCouponFixed fixedCouponAnnuity) {
      return null;
    }

    @Override
    public String visitForwardLiborAnnuity(AnnuityCouponIbor forwardLiborAnnuity) {
      return null;
    }

    @Override
    public String visitAnnuityCouponIborRatchet(AnnuityCouponIborRatchet annuity) {
      return null;
    }

    @Override
    public String visitSwap(Swap<?, ?> swap) {
      return null;
    }

    @Override
    public String visitFixedCouponSwap(SwapFixedCoupon<?> swap) {
      return null;
    }

    @Override
    public String visitFixedFloatSwap(FixedFloatSwap swap) {
      return null;
    }

    @Override
    public String visitSwaptionCashFixedIbor(SwaptionCashFixedIbor swaption) {
      return null;
    }

    @Override
    public String visitSwaptionPhysicalFixedIbor(SwaptionPhysicalFixedIbor swaption) {
      return null;
    }

    @Override
    public String visitSwaptionBermudaFixedIbor(SwaptionBermudaFixedIbor swaption) {
      return null;
    }

    @Override
    public String visitFloatingRateNote(FloatingRateNote frn) {
      return null;
    }

    @Override
    public String visitCrossCurrencySwap(CrossCurrencySwap ccs) {
      return null;
    }

    @Override
    public String visitForexForward(ForexForward fx) {
      return null;
    }

    @Override
    public String visitTenorSwap(TenorSwap<? extends Payment> tenorSwap) {
      return null;
    }

    @Override
    public String visitCash(Cash cash) {
      return null;
    }

    @Override
    public String visitBondFuture(BondFuture future) {
      return null;
    }

    @Override
    public String visitInterestRateFuture(InterestRateFuture future) {
      return null;
    }

    @Override
    public String visitInterestRateFutureOptionPremiumSecurity(InterestRateFutureOptionPremiumSecurity option) {
      return null;
    }

    @Override
    public String visitInterestRateFutureOptionPremiumTransaction(InterestRateFutureOptionPremiumTransaction option) {
      return null;
    }

    @Override
    public String visitInterestRateFutureOptionMarginSecurity(InterestRateFutureOptionMarginSecurity option) {
      return null;
    }

    @Override
    public String visitInterestRateFutureOptionMarginTransaction(InterestRateFutureOptionMarginTransaction option) {
      return null;
    }

    @Override
    public String visitFixedPayment(PaymentFixed payment) {
      return null;
    }

    @Override
    public String visitCouponFixed(CouponFixed payment) {
      return null;
    }

    @Override
    public String visitCouponIborSpread(CouponIborSpread payment) {
      return null;
    }

    @Override
    public String visitCouponIborGearing(CouponIborGearing payment) {
      return null;
    }

    @Override
    public String visitCouponOIS(CouponOIS payment) {
      return null;
    }

    @Override
    public String visitCouponCMS(CouponCMS payment) {
      return null;
    }

    @Override
    public String visitCapFloorIbor(CapFloorIbor payment) {
      return null;
    }

    @Override
    public String visitCapFloorCMS(CapFloorCMS payment) {
      return null;
    }

    @Override
    public String visitCapFloorCMSSpread(CapFloorCMSSpread payment) {
      return null;
    }

    @Override
    public String visitForwardRateAgreement(ForwardRateAgreement fra) {
      return null;
    }

    @Override
    public String visitCouponInflationZeroCouponMonthly(CouponInflationZeroCouponMonthly coupon) {
      return null;
    }

    @Override
    public String visitCouponInflationZeroCouponMonthlyGearing(CouponInflationZeroCouponMonthlyGearing coupon) {
      return null;
    }

    @Override
    public String visitCouponInflationZeroCouponInterpolation(CouponInflationZeroCouponInterpolation coupon) {
      return null;
    }

    @Override
    public String visitCouponInflationZeroCouponInterpolationGearing(CouponInflationZeroCouponInterpolationGearing coupon) {
      return null;
    }

    @Override
    public String visitBondCapitalIndexedSecurity(BondCapitalIndexedSecurity<?> bond) {
      return null;
    }

    @Override
    public String visitBondCapitalIndexedTransaction(BondCapitalIndexedTransaction<?> bond) {
      return null;
    }

    @Override
    public String visitDepositIbor(DepositIbor deposit, T data) {
      return null;
    }

    @Override
    public String visitDepositIbor(DepositIbor deposit) {
      return null;
    }

    @Override
    public String visitDepositCounterpart(DepositCounterpart deposit, T data) {
      return null;
    }

    @Override
    public String visitDepositCounterpart(DepositCounterpart deposit) {
      return null;
    }

    @Override
    public String visitForexOptionDigital(final ForexOptionDigital derivative, final T data) {
      return "ForexOptionDigital2";
    }

    @Override
    public String visitForexOptionDigital(final ForexOptionDigital derivative) {
      return "ForexOptionDigital1";
    }

    @Override
    public String visitBillSecurity(BillSecurity bill, T data) {
      return null;
    }

    @Override
    public String visitBillSecurity(BillSecurity bill) {
      return null;
    }

    @Override
    public String visitBillTransaction(BillTransaction bill, T data) {
      return null;
    }

    @Override
    public String visitBillTransaction(BillTransaction bill) {
      return null;
    }

    @Override
    public String visitFederalFundsFutureSecurity(FederalFundsFutureSecurity future, T data) {
      return null;
    }

    @Override
    public String visitFederalFundsFutureSecurity(FederalFundsFutureSecurity future) {
      return null;
    }

    @Override
    public String visitFederalFundsFutureTransaction(FederalFundsFutureTransaction future, T data) {
      return null;
    }

    @Override
    public String visitFederalFundsFutureTransaction(FederalFundsFutureTransaction future) {
      return null;
    }

    @Override
    public String visitDepositZero(DepositZero deposit, T data) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String visitDepositZero(DepositZero deposit) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String visitBondFutureOptionPremiumSecurity(BondFutureOptionPremiumSecurity option, T data) {
      return null;
    }

    @Override
    public String visitBondFutureOptionPremiumSecurity(BondFutureOptionPremiumSecurity option) {
      return null;
    }

    @Override
    public String visitBondFutureOptionPremiumTransaction(BondFutureOptionPremiumTransaction option, T data) {
      return null;
    }

    @Override
    public String visitBondFutureOptionPremiumTransaction(BondFutureOptionPremiumTransaction option) {
      return null;
    }

    @Override
    public String visitCouponIbor(CouponIbor payment, T data) {
      return null;
    }

    @Override
    public String visitCouponIbor(CouponIbor payment) {
      return null;
    }

    @Override
    public String visitAnnuityCouponIborSpread(AnnuityCouponIborSpread annuity, T data) {
      return null;
    }

    @Override
    public String visitAnnuityCouponIborSpread(AnnuityCouponIborSpread annuity) {
      return null;
    }

    @Override
    public String visitCouponIborCompounded(CouponIborCompounded payment) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String visitCouponIborCompounded(CouponIborCompounded payment, T data) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String visitCDSDerivative(ISDACDSDerivative cds, T data) {
      return null;
    }

    @Override
    public String visitCDSDerivative(ISDACDSDerivative cds) {
      return null;
    }

  }

  private static class MyAbstractVisitor<T, U> extends AbstractInstrumentDerivativeVisitor<T, String> {

  }

}
