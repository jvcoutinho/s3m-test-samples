/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.engine.view.compilation;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Supplier;
import com.opengamma.OpenGammaRuntimeException;
import com.opengamma.core.position.Portfolio;
import com.opengamma.core.security.SecuritySource;
import com.opengamma.engine.ComputationTargetType;
import com.opengamma.engine.depgraph.DependencyGraph;
import com.opengamma.engine.depgraph.DependencyGraphBuilder;
import com.opengamma.engine.depgraph.DependencyNode;
import com.opengamma.engine.depgraph.DependencyNodeFormatter;
import com.opengamma.engine.depgraph.Housekeeper;
import com.opengamma.engine.value.ValueRequirement;
import com.opengamma.engine.value.ValueSpecification;
import com.opengamma.engine.view.ViewCalculationConfiguration;
import com.opengamma.engine.view.ViewDefinition;
import com.opengamma.id.VersionCorrection;
import com.opengamma.util.ArgumentChecker;
import com.opengamma.util.monitor.OperationTimer;
import com.opengamma.util.tuple.Pair;

/**
 * Ultimately produces a set of {@link DependencyGraph}s from a {@link ViewDefinition}, one for each {@link ViewCalculationConfiguration}. Additional information, such as the live data requirements,
 * is collected along the way and exposed after compilation.
 * <p>
 * The compiled graphs are guaranteed to be calculable for at least the requested timestamp. One or more of the referenced functions may not be valid at other timestamps.
 */
public final class ViewDefinitionCompiler {

  private static final Logger s_logger = LoggerFactory.getLogger(ViewDefinitionCompiler.class);
  private static final boolean OUTPUT_DEPENDENCY_GRAPHS = false;
  private static final boolean OUTPUT_LIVE_DATA_REQUIREMENTS = false;
  private static final boolean OUTPUT_FAILURE_REPORTS = false;

  private ViewDefinitionCompiler() {
  }

  /**
   * Exposure of the completion status of a graph compilation. This is currently for debugging/diagnostic purposes. A full implementation should incorporate the "cancel"/"get" behavior of the Future
   * returned by {@link #compileTask} with something that can return the progress of the task.
   */
  protected static final class CompilationCompletionEstimate implements Housekeeper.Callback<Supplier<Double>> {

    private final String _label;
    private final ConcurrentMap<String, Double> _buildEstimates;

    private CompilationCompletionEstimate(final ViewCompilationContext context) {
      final Collection<DependencyGraphBuilder> builders = context.getBuilders();
      _buildEstimates = new ConcurrentHashMap<String, Double>();
      for (DependencyGraphBuilder builder : builders) {
        _buildEstimates.put(builder.getCalculationConfigurationName(), 0d);
        Housekeeper.of(builder, this, builder.buildFractionEstimate()).start();
      }
      _label = context.getViewDefinition().getName();
    }

    public double[] estimates() {
      final double[] result = new double[_buildEstimates.size()];
      int i = 0;
      for (Double estimate : _buildEstimates.values()) {
        result[i++] = estimate;
      }
      return result;
    }

    public double estimate() {
      double result = 0;
      for (Double estimate : _buildEstimates.values()) {
        result += estimate;
      }
      return result / _buildEstimates.size();
    }

    @Override
    public boolean tick(final DependencyGraphBuilder builder, final Supplier<Double> estimate) {
      final Double estimateValue = estimate.get();
      s_logger.debug("{}/{} building at {}", new Object[] {_label, builder.getCalculationConfigurationName(), estimateValue });
      _buildEstimates.put(builder.getCalculationConfigurationName(), estimateValue);
      return estimateValue < 1d;
    }

    @Override
    public boolean cancelled(final DependencyGraphBuilder builder, final Supplier<Double> estimate) {
      return false;
    }

    @Override
    public boolean completed(final DependencyGraphBuilder builder, final Supplier<Double> estimate) {
      return estimate.get() < 1d;
    }

  }

  //-------------------------------------------------------------------------
  public static Future<CompiledViewDefinitionWithGraphsImpl> compileTask(final ViewDefinition viewDefinition, final ViewCompilationServices compilationServices, final Instant valuationTime,
      final VersionCorrection versionCorrection) {
    ArgumentChecker.notNull(viewDefinition, "viewDefinition");
    ArgumentChecker.notNull(compilationServices, "compilationServices");
    s_logger.debug("Compiling {} for use with {}", viewDefinition.getName(), valuationTime);
    final OperationTimer timer = new OperationTimer(s_logger, "Compiling ViewDefinition: {}", viewDefinition.getName());
    final ViewCompilationContext viewCompilationContext = new ViewCompilationContext(viewDefinition, compilationServices, valuationTime);
    if (s_logger.isDebugEnabled()) {
      new CompilationCompletionEstimate(viewCompilationContext);
    }
    // TODO: return a Future that provides access to a completion metric to feedback to any interactive user
    return new Future<CompiledViewDefinitionWithGraphsImpl>() {
      
      private volatile CompiledViewDefinitionWithGraphsImpl _result;

      /**
       * Cancels any active builders.
       */
      @Override
      public boolean cancel(final boolean mayInterruptIfRunning) {
        boolean result = true;
        for (DependencyGraphBuilder builder : viewCompilationContext.getBuilders()) {
          result &= builder.cancel(mayInterruptIfRunning);
        }
        return result;
      }

      /**
       * Tests if any of the builders have been canceled.
       */
      @Override
      public boolean isCancelled() {
        boolean result = false;
        for (DependencyGraphBuilder builder : viewCompilationContext.getBuilders()) {
          result |= builder.isCancelled();
        }
        return result;
      }

      /**
       * Tests if all of the builders have completed.
       */
      @Override
      public boolean isDone() {
        return _result != null;
      }
      
      @Override
      public CompiledViewDefinitionWithGraphsImpl get() throws InterruptedException, ExecutionException {
        long t = -System.nanoTime();
        EnumSet<ComputationTargetType> specificTargetTypes = SpecificRequirementsCompiler.execute(viewCompilationContext);
        t += System.nanoTime();
        s_logger.info("Added specific requirements after {}ms", (double) t / 1e6);
        t -= System.nanoTime();
        boolean requirePortfolioResolution = specificTargetTypes.contains(ComputationTargetType.PORTFOLIO_NODE) || specificTargetTypes.contains(ComputationTargetType.POSITION);
        Portfolio portfolio = PortfolioCompiler.execute(viewCompilationContext, versionCorrection, requirePortfolioResolution);
        t += System.nanoTime();
        s_logger.info("Added portfolio requirements after {}ms", (double) t / 1e6);
        t -= System.nanoTime();
        Map<String, DependencyGraph> graphsByConfiguration = processDependencyGraphs(viewCompilationContext);
        t += System.nanoTime();
        s_logger.info("Processed dependency graphs after {}ms", (double) t / 1e6);
        timer.finished();
        _result = new CompiledViewDefinitionWithGraphsImpl(viewDefinition, graphsByConfiguration, portfolio, compilationServices.getFunctionCompilationContext().getFunctionInitId());
        if (OUTPUT_DEPENDENCY_GRAPHS) {
          outputDependencyGraphs(graphsByConfiguration);
        }
        if (OUTPUT_LIVE_DATA_REQUIREMENTS) {
          outputLiveDataRequirements(graphsByConfiguration, compilationServices.getComputationTargetResolver().getSecuritySource());
        }
        if (OUTPUT_FAILURE_REPORTS) {
          outputFailureReports(viewCompilationContext.getBuilders());
        }
        return _result;
      }

      @Override
      public CompiledViewDefinitionWithGraphsImpl get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        throw new UnsupportedOperationException();
      }

    };
  }

  public static CompiledViewDefinitionWithGraphsImpl compile(ViewDefinition viewDefinition, ViewCompilationServices compilationServices, Instant valuationTime, VersionCorrection versionCorrection) {
    try {
      return compileTask(viewDefinition, compilationServices, valuationTime, versionCorrection).get();
    } catch (InterruptedException e) {
      throw new OpenGammaRuntimeException("Interrupted", e);
    } catch (ExecutionException e) {
      throw new OpenGammaRuntimeException("Failed", e);
    }
  }

  private static Map<String, DependencyGraph> processDependencyGraphs(final ViewCompilationContext context) {
    final Collection<DependencyGraphBuilder> builders = context.getBuilders();
    final Map<String, DependencyGraph> result = new HashMap<String, DependencyGraph>();
    for (DependencyGraphBuilder builder : builders) {
      final DependencyGraph graph = builder.getDependencyGraph();
      graph.removeUnnecessaryValues();
      result.put(builder.getCalculationConfigurationName(), graph);
      // TODO: do we want to do anything with the ValueRequirement to resolved ValueSpecification data?
    }
    return result;
  }

  private static void outputDependencyGraphs(Map<String, DependencyGraph> graphsByConfiguration) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, DependencyGraph> entry : graphsByConfiguration.entrySet()) {
      String configName = entry.getKey();
      sb.append("DepGraph for ").append(configName);

      DependencyGraph depGraph = entry.getValue();
      sb.append("\tProducing values ").append(depGraph.getOutputSpecifications());
      for (DependencyNode depNode : depGraph.getDependencyNodes()) {
        sb.append("\t\tNode:\n").append(DependencyNodeFormatter.toString(depNode));
      }
    }
    s_logger.warn("Dependency Graphs -- \n{}", sb);
  }

  private static void outputLiveDataRequirements(Map<String, DependencyGraph> graphsByConfiguration, SecuritySource secMaster) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, DependencyGraph> entry : graphsByConfiguration.entrySet()) {
      String configName = entry.getKey();
      Collection<Pair<ValueRequirement, ValueSpecification>> requiredLiveData = entry.getValue().getAllRequiredMarketData();
      if (requiredLiveData.isEmpty()) {
        sb.append(configName).append(" requires no live data.\n");
      } else {
        sb.append("Live data for ").append(configName).append("\n");
        for (Pair<ValueRequirement, ValueSpecification> liveRequirement : requiredLiveData) {
          sb.append("\t").append(liveRequirement.getFirst()).append("\n");
        }
      }
    }
    s_logger.warn("Live data requirements -- \n{}", sb);
  }

  private static void outputFailureReports(final Collection<DependencyGraphBuilder> builders) {
    for (DependencyGraphBuilder builder : builders) {
      outputFailureReport(builder);
    }
  }

  public static void outputFailureReport(final DependencyGraphBuilder builder) {
    final Map<Throwable, Integer> exceptions = builder.getExceptions();
    if (!exceptions.isEmpty()) {
      for (Map.Entry<Throwable, Integer> entry : exceptions.entrySet()) {
        final Throwable exception = entry.getKey();
        final Integer count = entry.getValue();
        if (exception.getCause() != null) {
          if (s_logger.isDebugEnabled()) {
            s_logger.debug("Nested exception raised " + count + " time(s)", exception);
          }
        } else {
          if (s_logger.isWarnEnabled()) {
            s_logger.warn("Exception raised " + count + " time(s)", exception);
          }
        }
      }
    } else {
      s_logger.info("No exceptions raised for configuration {}", builder.getCalculationConfigurationName());
    }
  }

}
