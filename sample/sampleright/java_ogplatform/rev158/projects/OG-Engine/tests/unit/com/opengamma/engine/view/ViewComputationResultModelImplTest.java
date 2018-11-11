/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */

package com.opengamma.engine.view;

import static com.opengamma.engine.view.ViewCalculationResultModelImplTest.COMPUTED_VALUE;
import static com.opengamma.engine.view.ViewCalculationResultModelImplTest.PORTFOLIO;
import static com.opengamma.engine.view.ViewCalculationResultModelImplTest.PORTFOLIO_ROOT_NODE;
import static com.opengamma.engine.view.ViewCalculationResultModelImplTest.SPEC;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.Set;

import javax.time.Instant;

import org.junit.Test;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.opengamma.engine.ComputationTargetSpecification;
import com.opengamma.engine.value.ComputedValue;

/**
 * 
 */
public class ViewComputationResultModelImplTest {
  
  @Test
  public void test() {
    InMemoryViewComputationResultModel model = new InMemoryViewComputationResultModel();
    checkModel(model);
  }

  public static void checkModel(InMemoryViewResultModel model) {
    model.setEvaluationTime(Instant.ofEpochMillis(400));
    assertEquals(Instant.ofEpochMillis(400), model.getEvaluationTime());
    model.setResultTimestamp(Instant.ofEpochMillis(500));
    assertEquals(Instant.ofEpochMillis(500), model.getResultTimestamp());
    
    Set<String> calcConfigNames = Sets.newHashSet("configName1", "configName2");
    model.setCalculationConfigurationNames(calcConfigNames);
    assertEquals(calcConfigNames, model.getCalculationConfigurationNames());
    
    model.setPortfolio(PORTFOLIO);
    model.addValue("configName1", COMPUTED_VALUE);
    
    assertEquals(Sets.newHashSet(SPEC, new ComputationTargetSpecification(PORTFOLIO_ROOT_NODE)), Sets.newHashSet(model.getAllTargets()));
    
    ViewCalculationResultModel calcResult = model.getCalculationResult("configName1");
    assertNotNull(calcResult);
    
    HashMap<String, ComputedValue> expectedMap = new HashMap<String, ComputedValue>();
    expectedMap.put("DATA", COMPUTED_VALUE);
    assertEquals(expectedMap, Maps.newHashMap(calcResult.getValues(SPEC)));
  }

}
