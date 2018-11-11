/**
 * Copyright (C) 2012 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.analytics.financial.instrument.index;

import javax.time.calendar.ZonedDateTime;

import com.opengamma.analytics.financial.instrument.bond.BillSecurityDefinition;
import com.opengamma.analytics.financial.instrument.bond.BillTransactionDefinition;
import com.opengamma.analytics.financial.schedule.ScheduleCalculator;
import com.opengamma.util.ArgumentChecker;

/**
 * Class use to generate Bill transactions.
 */
public class GeneratorBill extends GeneratorInstrument<GeneratorAttribute> {

  /**
   * The underlying bill security.
   */
  private final BillSecurityDefinition _security;

  /**
   * Constructor.
   * @param name Generator name.
   * @param security The underlying bill security.
   */
  public GeneratorBill(String name, BillSecurityDefinition security) {
    super(name);
    ArgumentChecker.notNull(security, "Bill security");
    _security = security;
  }

  @Override
  /**
   * Generate a bill transaction from the bill (market quote) yield.
   */
  public BillTransactionDefinition generateInstrument(final ZonedDateTime date, final double marketQuote, final double notional, final GeneratorAttribute attribute) {
    ArgumentChecker.notNull(date, "Reference date");
    int quantity = (int) Math.round(notional / _security.getNotional());
    ZonedDateTime settleDate = ScheduleCalculator.getAdjustedDate(date, _security.getSettlementDays(), _security.getCalendar());
    return BillTransactionDefinition.fromYield(_security, quantity, settleDate, marketQuote);
  }

}
