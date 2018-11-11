/**
 * Copyright (C) 2011 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.analytics.financial.forex.calculator;

import static org.testng.AssertJUnit.assertEquals;

import org.testng.annotations.Test;

import com.opengamma.analytics.financial.forex.definition.ForexDefinition;
import com.opengamma.analytics.financial.forex.definition.ForexNonDeliverableForwardDefinition;
import com.opengamma.analytics.financial.forex.definition.ForexNonDeliverableOptionDefinition;
import com.opengamma.analytics.financial.forex.definition.ForexOptionDigitalDefinition;
import com.opengamma.analytics.financial.forex.definition.ForexOptionSingleBarrierDefinition;
import com.opengamma.analytics.financial.forex.definition.ForexOptionVanillaDefinition;
import com.opengamma.analytics.financial.forex.definition.ForexSwapDefinition;
import com.opengamma.analytics.financial.instrument.InstrumentDefinition;
import com.opengamma.analytics.financial.instrument.InstrumentDefinitionVisitor;
import com.opengamma.analytics.financial.instrument.annuity.AnnuityDefinition;
import com.opengamma.analytics.financial.instrument.bond.BillSecurityDefinition;
import com.opengamma.analytics.financial.instrument.bond.BillTransactionDefinition;
import com.opengamma.analytics.financial.instrument.bond.BondCapitalIndexedSecurityDefinition;
import com.opengamma.analytics.financial.instrument.bond.BondCapitalIndexedTransactionDefinition;
import com.opengamma.analytics.financial.instrument.bond.BondFixedSecurityDefinition;
import com.opengamma.analytics.financial.instrument.bond.BondFixedTransactionDefinition;
import com.opengamma.analytics.financial.instrument.bond.BondIborSecurityDefinition;
import com.opengamma.analytics.financial.instrument.bond.BondIborTransactionDefinition;
import com.opengamma.analytics.financial.instrument.cash.CashDefinition;
import com.opengamma.analytics.financial.instrument.cash.DepositCounterpartDefinition;
import com.opengamma.analytics.financial.instrument.cash.DepositIborDefinition;
import com.opengamma.analytics.financial.instrument.cash.DepositZeroDefinition;
import com.opengamma.analytics.financial.instrument.cds.ISDACDSDefinition;
import com.opengamma.analytics.financial.instrument.fra.ForwardRateAgreementDefinition;
import com.opengamma.analytics.financial.instrument.future.BondFutureDefinition;
import com.opengamma.analytics.financial.instrument.future.BondFutureOptionPremiumSecurityDefinition;
import com.opengamma.analytics.financial.instrument.future.BondFutureOptionPremiumTransactionDefinition;
import com.opengamma.analytics.financial.instrument.future.FederalFundsFutureSecurityDefinition;
import com.opengamma.analytics.financial.instrument.future.FederalFundsFutureTransactionDefinition;
import com.opengamma.analytics.financial.instrument.future.InterestRateFutureDefinition;
import com.opengamma.analytics.financial.instrument.future.InterestRateFutureOptionMarginSecurityDefinition;
import com.opengamma.analytics.financial.instrument.future.InterestRateFutureOptionMarginTransactionDefinition;
import com.opengamma.analytics.financial.instrument.future.InterestRateFutureOptionPremiumSecurityDefinition;
import com.opengamma.analytics.financial.instrument.future.InterestRateFutureOptionPremiumTransactionDefinition;
import com.opengamma.analytics.financial.instrument.inflation.CouponInflationZeroCouponInterpolationDefinition;
import com.opengamma.analytics.financial.instrument.inflation.CouponInflationZeroCouponInterpolationGearingDefinition;
import com.opengamma.analytics.financial.instrument.inflation.CouponInflationZeroCouponMonthlyDefinition;
import com.opengamma.analytics.financial.instrument.inflation.CouponInflationZeroCouponMonthlyGearingDefinition;
import com.opengamma.analytics.financial.instrument.payment.CapFloorCMSDefinition;
import com.opengamma.analytics.financial.instrument.payment.CapFloorCMSSpreadDefinition;
import com.opengamma.analytics.financial.instrument.payment.CapFloorIborDefinition;
import com.opengamma.analytics.financial.instrument.payment.CouponCMSDefinition;
import com.opengamma.analytics.financial.instrument.payment.CouponFixedDefinition;
import com.opengamma.analytics.financial.instrument.payment.CouponIborCompoundedDefinition;
import com.opengamma.analytics.financial.instrument.payment.CouponIborDefinition;
import com.opengamma.analytics.financial.instrument.payment.CouponIborGearingDefinition;
import com.opengamma.analytics.financial.instrument.payment.CouponIborRatchetDefinition;
import com.opengamma.analytics.financial.instrument.payment.CouponIborSpreadDefinition;
import com.opengamma.analytics.financial.instrument.payment.CouponOISDefinition;
import com.opengamma.analytics.financial.instrument.payment.CouponOISSimplifiedDefinition;
import com.opengamma.analytics.financial.instrument.payment.PaymentDefinition;
import com.opengamma.analytics.financial.instrument.payment.PaymentFixedDefinition;
import com.opengamma.analytics.financial.instrument.swap.SwapDefinition;
import com.opengamma.analytics.financial.instrument.swap.SwapFixedIborDefinition;
import com.opengamma.analytics.financial.instrument.swap.SwapFixedIborSpreadDefinition;
import com.opengamma.analytics.financial.instrument.swap.SwapIborIborDefinition;
import com.opengamma.analytics.financial.instrument.swap.SwapXCcyIborIborDefinition;
import com.opengamma.analytics.financial.instrument.swaption.SwaptionBermudaFixedIborDefinition;
import com.opengamma.analytics.financial.instrument.swaption.SwaptionCashFixedIborDefinition;
import com.opengamma.analytics.financial.instrument.swaption.SwaptionPhysicalFixedIborDefinition;
import com.opengamma.analytics.financial.instrument.swaption.SwaptionPhysicalFixedIborSpreadDefinition;

/**
 * Tests the visitor of Forex definitions.
 */
public class ForexDefinitionVisitorTest {

  private static final ForexDefinition FX_DEFINITION = ForexInstrumentsDescriptionDataSet.createForexDefinition();
  private static final ForexSwapDefinition FX_SWAP_DEFINITION = ForexInstrumentsDescriptionDataSet.createForexSwapDefinition();
  private static final ForexOptionVanillaDefinition FX_OPTION_DEFINITION = ForexInstrumentsDescriptionDataSet.createForexOptionVanillaDefinition();
  private static final ForexOptionSingleBarrierDefinition FX_SINGLE_BARRIER_OPTION_DEFINITION = ForexInstrumentsDescriptionDataSet.createForexOptionSingleBarrierDefinition();
  private static final ForexNonDeliverableForwardDefinition NDF_DEFINITION = ForexInstrumentsDescriptionDataSet.createForexNonDeliverableForwardDefinition();
  private static final ForexNonDeliverableOptionDefinition NDO_DEFINITION = ForexInstrumentsDescriptionDataSet.createForexNonDeliverableOptionDefinition();
  private static final ForexOptionDigitalDefinition FX_OPTION_DIGITAL_DEFINITION = ForexInstrumentsDescriptionDataSet.createForexOptionDigitalDefinition();

  @SuppressWarnings("synthetic-access")
  private static final MyVisitor<Object, String> VISITOR = new MyVisitor<Object, String>();

  @Test
  public void testVisitor() {
    final Object o = "G";
    assertEquals(FX_DEFINITION.accept(VISITOR), "Forex1");
    assertEquals(FX_DEFINITION.accept(VISITOR, o), "Forex2");
    assertEquals(FX_SWAP_DEFINITION.accept(VISITOR), "ForexSwap1");
    assertEquals(FX_SWAP_DEFINITION.accept(VISITOR, o), "ForexSwap2");
    assertEquals(FX_OPTION_DEFINITION.accept(VISITOR), "ForexOptionVanilla1");
    assertEquals(FX_OPTION_DEFINITION.accept(VISITOR, o), "ForexOptionVanilla2");
    assertEquals(FX_SINGLE_BARRIER_OPTION_DEFINITION.accept(VISITOR, o), "ForexOptionSingleBarrier2");
    assertEquals(FX_SINGLE_BARRIER_OPTION_DEFINITION.accept(VISITOR), "ForexOptionSingleBarrier1");
    assertEquals(NDF_DEFINITION.accept(VISITOR), "ForexNonDeliverableForwardDefinition1");
    assertEquals(NDF_DEFINITION.accept(VISITOR, o), "ForexNonDeliverableForwardDefinition2");
    assertEquals(NDO_DEFINITION.accept(VISITOR), "ForexNonDeliverableOptionDefinition1");
    assertEquals(NDO_DEFINITION.accept(VISITOR, o), "ForexNonDeliverableOptionDefinition2");
    assertEquals(FX_OPTION_DIGITAL_DEFINITION.accept(VISITOR), "ForexOptionDigital1");
    assertEquals(FX_OPTION_DIGITAL_DEFINITION.accept(VISITOR, o), "ForexOptionDigital2");
  }

  private static class MyVisitor<T, U> implements InstrumentDefinitionVisitor<T, String> {

    @Override
    public String visit(final InstrumentDefinition<?> definition, final T data) {
      return null;
    }

    @Override
    public String visit(final InstrumentDefinition<?> definition) {
      return null;
    }

    @Override
    public String visitForexDefinition(final ForexDefinition fx, final T data) {
      return "Forex2";
    }

    @Override
    public String visitForexDefinition(final ForexDefinition fx) {
      return "Forex1";
    }

    @Override
    public String visitForexSwapDefinition(final ForexSwapDefinition fx, final T data) {
      return "ForexSwap2";
    }

    @Override
    public String visitForexSwapDefinition(final ForexSwapDefinition fx) {
      return "ForexSwap1";
    }

    @Override
    public String visitForexOptionVanillaDefinition(final ForexOptionVanillaDefinition fx, final T data) {
      return "ForexOptionVanilla2";
    }

    @Override
    public String visitForexOptionVanillaDefinition(final ForexOptionVanillaDefinition fx) {
      return "ForexOptionVanilla1";
    }

    @Override
    public String visitForexOptionSingleBarrierDefiniton(final ForexOptionSingleBarrierDefinition fx, final T data) {
      return "ForexOptionSingleBarrier2";
    }

    @Override
    public String visitForexOptionSingleBarrierDefiniton(final ForexOptionSingleBarrierDefinition fx) {
      return "ForexOptionSingleBarrier1";
    }

    @Override
    public String visitForexNonDeliverableForwardDefinition(ForexNonDeliverableForwardDefinition ndf, T data) {
      return "ForexNonDeliverableForwardDefinition2";
    }

    @Override
    public String visitForexNonDeliverableForwardDefinition(ForexNonDeliverableForwardDefinition ndf) {
      return "ForexNonDeliverableForwardDefinition1";
    }

    @Override
    public String visitForexNonDeliverableOptionDefinition(ForexNonDeliverableOptionDefinition ndo, T data) {
      return "ForexNonDeliverableOptionDefinition2";
    }

    @Override
    public String visitForexNonDeliverableOptionDefinition(ForexNonDeliverableOptionDefinition ndo) {
      return "ForexNonDeliverableOptionDefinition1";
    }

    @Override
    public String visitBondFixedSecurityDefinition(BondFixedSecurityDefinition bond, T data) {
      return null;
    }

    @Override
    public String visitBondFixedSecurityDefinition(BondFixedSecurityDefinition bond) {
      return null;
    }

    @Override
    public String visitBondFixedTransactionDefinition(BondFixedTransactionDefinition bond, T data) {
      return null;
    }

    @Override
    public String visitBondFixedTransactionDefinition(BondFixedTransactionDefinition bond) {
      return null;
    }

    @Override
    public String visitBondFutureSecurityDefinition(BondFutureDefinition bond, T data) {
      return null;
    }

    @Override
    public String visitBondFutureSecurityDefinition(BondFutureDefinition bond) {
      return null;
    }

    @Override
    public String visitBondIborTransactionDefinition(BondIborTransactionDefinition bond, T data) {
      return null;
    }

    @Override
    public String visitBondIborTransactionDefinition(BondIborTransactionDefinition bond) {
      return null;
    }

    @Override
    public String visitBondIborSecurityDefinition(BondIborSecurityDefinition bond, T data) {
      return null;
    }

    @Override
    public String visitBondIborSecurityDefinition(BondIborSecurityDefinition bond) {
      return null;
    }

    @Override
    public String visitCashDefinition(CashDefinition cash, T data) {
      return null;
    }

    @Override
    public String visitCashDefinition(CashDefinition cash) {
      return null;
    }

    @Override
    public String visitForwardRateAgreementDefinition(ForwardRateAgreementDefinition fra, T data) {
      return null;
    }

    @Override
    public String visitForwardRateAgreementDefinition(ForwardRateAgreementDefinition fra) {
      return null;
    }

    @Override
    public String visitInterestRateFutureSecurityDefinition(InterestRateFutureDefinition future, T data) {
      return null;
    }

    @Override
    public String visitInterestRateFutureSecurityDefinition(InterestRateFutureDefinition future) {
      return null;
    }

    @Override
    public String visitInterestRateFutureOptionPremiumSecurityDefinition(InterestRateFutureOptionPremiumSecurityDefinition future, T data) {
      return null;
    }

    @Override
    public String visitInterestRateFutureOptionPremiumSecurityDefinition(InterestRateFutureOptionPremiumSecurityDefinition future) {
      return null;
    }

    @Override
    public String visitInterestRateFutureOptionPremiumTransactionDefinition(InterestRateFutureOptionPremiumTransactionDefinition future, T data) {
      return null;
    }

    @Override
    public String visitInterestRateFutureOptionPremiumTransactionDefinition(InterestRateFutureOptionPremiumTransactionDefinition future) {
      return null;
    }

    @Override
    public String visitInterestRateFutureOptionMarginSecurityDefinition(InterestRateFutureOptionMarginSecurityDefinition future, T data) {
      return null;
    }

    @Override
    public String visitInterestRateFutureOptionMarginSecurityDefinition(InterestRateFutureOptionMarginSecurityDefinition future) {
      return null;
    }

    @Override
    public String visitInterestRateFutureOptionMarginTransactionDefinition(InterestRateFutureOptionMarginTransactionDefinition future, T data) {
      return null;
    }

    @Override
    public String visitInterestRateFutureOptionMarginTransactionDefinition(InterestRateFutureOptionMarginTransactionDefinition future) {
      return null;
    }

    @Override
    public String visitPaymentFixed(PaymentFixedDefinition payment, T data) {
      return null;
    }

    @Override
    public String visitPaymentFixed(PaymentFixedDefinition payment) {
      return null;
    }

    @Override
    public String visitCouponFixed(CouponFixedDefinition payment, T data) {
      return null;
    }

    @Override
    public String visitCouponFixed(CouponFixedDefinition payment) {
      return null;
    }

    @Override
    public String visitCouponIbor(CouponIborDefinition payment, T data) {
      return null;
    }

    @Override
    public String visitCouponIbor(CouponIborDefinition payment) {
      return null;
    }

    @Override
    public String visitCouponIborSpread(CouponIborSpreadDefinition payment, T data) {
      return null;
    }

    @Override
    public String visitCouponIborSpread(CouponIborSpreadDefinition payment) {
      return null;
    }

    @Override
    public String visitCouponOISSimplified(CouponOISSimplifiedDefinition payment, T data) {
      return null;
    }

    @Override
    public String visitCouponOISSimplified(CouponOISSimplifiedDefinition payment) {
      return null;
    }

    @Override
    public String visitCouponOIS(CouponOISDefinition payment, T data) {
      return null;
    }

    @Override
    public String visitCouponOIS(CouponOISDefinition payment) {
      return null;
    }

    @Override
    public String visitCouponCMS(CouponCMSDefinition payment, T data) {
      return null;
    }

    @Override
    public String visitCouponCMS(CouponCMSDefinition payment) {
      return null;
    }

    @Override
    public String visitCapFloorCMS(CapFloorCMSDefinition payment, T data) {
      return null;
    }

    @Override
    public String visitCapFloorCMS(CapFloorCMSDefinition payment) {
      return null;
    }

    @Override
    public String visitAnnuityDefinition(AnnuityDefinition<? extends PaymentDefinition> annuity, T data) {
      return null;
    }

    @Override
    public String visitAnnuityDefinition(AnnuityDefinition<? extends PaymentDefinition> annuity) {
      return null;
    }

    @Override
    public String visitSwapDefinition(SwapDefinition swap, T data) {
      return null;
    }

    @Override
    public String visitSwapDefinition(SwapDefinition swap) {
      return null;
    }

    @Override
    public String visitSwapFixedIborDefinition(SwapFixedIborDefinition swap, T data) {
      return null;
    }

    @Override
    public String visitSwapFixedIborDefinition(SwapFixedIborDefinition swap) {
      return null;
    }

    @Override
    public String visitSwapFixedIborSpreadDefinition(SwapFixedIborSpreadDefinition swap, T data) {
      return null;
    }

    @Override
    public String visitSwapFixedIborSpreadDefinition(SwapFixedIborSpreadDefinition swap) {
      return null;
    }

    @Override
    public String visitSwapIborIborDefinition(SwapIborIborDefinition swap, T data) {
      return null;
    }

    @Override
    public String visitSwapIborIborDefinition(SwapIborIborDefinition swap) {
      return null;
    }

    @Override
    public String visitSwaptionCashFixedIborDefinition(SwaptionCashFixedIborDefinition swaption, T data) {
      return null;
    }

    @Override
    public String visitSwaptionCashFixedIborDefinition(SwaptionCashFixedIborDefinition swaption) {
      return null;
    }

    @Override
    public String visitSwaptionPhysicalFixedIborDefinition(SwaptionPhysicalFixedIborDefinition swaption, T data) {
      return null;
    }

    @Override
    public String visitSwaptionPhysicalFixedIborDefinition(SwaptionPhysicalFixedIborDefinition swaption) {
      return null;
    }

    @Override
    public String visitSwaptionBermudaFixedIborDefinition(SwaptionBermudaFixedIborDefinition swaption, T data) {
      return null;
    }

    @Override
    public String visitSwaptionBermudaFixedIborDefinition(SwaptionBermudaFixedIborDefinition swaption) {
      return null;
    }

    @Override
    public String visitCouponInflationZeroCouponFirstOfMonth(CouponInflationZeroCouponMonthlyDefinition coupon, T data) {
      return null;
    }

    @Override
    public String visitCouponInflationZeroCouponFirstOfMonth(CouponInflationZeroCouponMonthlyDefinition coupon) {
      return null;
    }

    @Override
    public String visitCouponInflationZeroCouponInterpolation(CouponInflationZeroCouponInterpolationDefinition coupon, T data) {
      return null;
    }

    @Override
    public String visitCouponInflationZeroCouponInterpolation(CouponInflationZeroCouponInterpolationDefinition coupon) {
      return null;
    }

    @Override
    public String visitCouponInflationZeroCouponMonthlyGearing(CouponInflationZeroCouponMonthlyGearingDefinition coupon, T data) {
      return null;
    }

    @Override
    public String visitCouponInflationZeroCouponMonthlyGearing(CouponInflationZeroCouponMonthlyGearingDefinition coupon) {
      return null;
    }

    @Override
    public String visitCouponInflationZeroCouponInterpolationGearing(CouponInflationZeroCouponInterpolationGearingDefinition coupon, T data) {
      return null;
    }

    @Override
    public String visitCouponInflationZeroCouponInterpolationGearing(CouponInflationZeroCouponInterpolationGearingDefinition coupon) {
      return null;
    }

    @Override
    public String visitBondCapitalIndexedSecurity(BondCapitalIndexedSecurityDefinition<?> bond, T data) {
      return null;
    }

    @Override
    public String visitBondCapitalIndexedSecurity(BondCapitalIndexedSecurityDefinition<?> bond) {
      return null;
    }

    @Override
    public String visitBondCapitalIndexedTransaction(BondCapitalIndexedTransactionDefinition<?> bond, T data) {
      return null;
    }

    @Override
    public String visitBondCapitalIndexedTransaction(BondCapitalIndexedTransactionDefinition<?> bond) {
      return null;
    }

    @Override
    public String visitDepositIborDefinition(DepositIborDefinition deposit, T data) {
      return null;
    }

    @Override
    public String visitDepositIborDefinition(DepositIborDefinition deposit) {
      return null;
    }

    @Override
    public String visitDepositCounterpartDefinition(DepositCounterpartDefinition deposit, T data) {
      return null;
    }

    @Override
    public String visitDepositCounterpartDefinition(DepositCounterpartDefinition deposit) {
      return null;
    }

    @Override
    public String visitForexOptionDigitalDefinition(ForexOptionDigitalDefinition fx, T data) {
      return "ForexOptionDigital2";
    }

    @Override
    public String visitForexOptionDigitalDefinition(ForexOptionDigitalDefinition fx) {
      return "ForexOptionDigital1";
    }

    @Override
    public String visitBillSecurityDefinition(BillSecurityDefinition bill, T data) {
      return null;
    }

    @Override
    public String visitBillSecurityDefinition(BillSecurityDefinition bill) {
      return null;
    }

    @Override
    public String visitBillTransactionDefinition(BillTransactionDefinition bill, T data) {
      return null;
    }

    @Override
    public String visitBillTransactionDefinition(BillTransactionDefinition bill) {
      return null;
    }

    @Override
    public String visitFederalFundsFutureSecurityDefinition(FederalFundsFutureSecurityDefinition future, T data) {
      return null;
    }

    @Override
    public String visitFederalFundsFutureSecurityDefinition(FederalFundsFutureSecurityDefinition future) {
      return null;
    }

    @Override
    public String visitFederalFundsFutureTransactionDefinition(FederalFundsFutureTransactionDefinition future, T data) {
      return null;
    }

    @Override
    public String visitFederalFundsFutureTransactionDefinition(FederalFundsFutureTransactionDefinition future) {
      return null;
    }

    @Override
    public String visitDepositZeroDefinition(DepositZeroDefinition deposit, T data) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String visitDepositZeroDefinition(DepositZeroDefinition deposit) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String visitBondFutureOptionPremiumSecurityDefinition(BondFutureOptionPremiumSecurityDefinition bond, T data) {
      return null;
    }

    @Override
    public String visitBondFutureOptionPremiumSecurityDefinition(BondFutureOptionPremiumSecurityDefinition bond) {
      return null;
    }

    @Override
    public String visitBondFutureOptionPremiumTransactionDefinition(BondFutureOptionPremiumTransactionDefinition bond, T data) {
      return null;
    }

    @Override
    public String visitBondFutureOptionPremiumTransactionDefinition(BondFutureOptionPremiumTransactionDefinition bond) {
      return null;
    }

    @Override
    public String visitSwapXCcyIborIborDefinition(SwapXCcyIborIborDefinition swap, T data) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String visitSwapXCcyIborIborDefinition(SwapXCcyIborIborDefinition swap) {
      // TODO Auto-generated method stub
      return null;
    }
    
        @Override
    public String visitCouponIborGearing(CouponIborGearingDefinition payment, T data) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String visitCouponIborGearing(CouponIborGearingDefinition payment) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String visitForwardRateAgreement(ForwardRateAgreementDefinition payment, T data) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String visitForwardRateAgreement(ForwardRateAgreementDefinition payment) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String visitCouponIborRatchet(CouponIborRatchetDefinition payment, T data) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String visitCouponIborRatchet(CouponIborRatchetDefinition payment) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String visitCapFloorIbor(CapFloorIborDefinition payment, T data) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String visitCapFloorIbor(CapFloorIborDefinition payment) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String visitCapFloorCMSSpread(CapFloorCMSSpreadDefinition payment, T data) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String visitCapFloorCMSSpread(CapFloorCMSSpreadDefinition payment) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String visitCouponIborCompounded(CouponIborCompoundedDefinition payment, T data) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String visitCouponIborCompounded(CouponIborCompoundedDefinition payment) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String visitSwaptionPhysicalFixedIborSpreadDefinition(SwaptionPhysicalFixedIborSpreadDefinition swaption, T data) {
      return null;
    }

    @Override
    public String visitSwaptionPhysicalFixedIborSpreadDefinition(SwaptionPhysicalFixedIborSpreadDefinition swaption) {
      return null;
    }

    @Override
    public String visitCDSDefinition(ISDACDSDefinition cds, T data) {
      return null;
    }

    @Override
    public String visitCDSDefinition(ISDACDSDefinition cds) {
      return null;
    }

  }

}
