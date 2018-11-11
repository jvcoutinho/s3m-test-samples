/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.engine.view.calc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opengamma.engine.depgraph.DependencyGraph;
import com.opengamma.engine.depgraph.DependencyNode;
import com.opengamma.engine.value.ValueSpecification;
import com.opengamma.engine.view.cache.CacheSelectHint;
import com.opengamma.engine.view.calc.stats.GraphExecutorStatisticsGatherer;
import com.opengamma.engine.view.calcnode.CalculationJob;
import com.opengamma.engine.view.calcnode.CalculationJobItem;
import com.opengamma.engine.view.calcnode.CalculationJobResult;
import com.opengamma.engine.view.calcnode.CalculationJobResultItem;
import com.opengamma.engine.view.calcnode.CalculationJobSpecification;
import com.opengamma.engine.view.calcnode.JobResultReceiver;
import com.opengamma.util.ArgumentChecker;
import com.opengamma.util.async.Cancelable;

/**
 * This DependencyGraphExecutor executes the given dependency graph
 * on a single calculation node in a single thread. Whether that node 
 * is the local machine or a remote machine on the grid depends on the
 * the {@code com.opengamma.engine.view.calcnode.JobRequestSender} configured in 
 * {@link com.opengamma.engine.view.ViewProcessContext}.
 * 
 */
public class SingleNodeExecutor implements DependencyGraphExecutor<ExecutionResult>, JobResultReceiver {

  private static final Logger s_logger = LoggerFactory.getLogger(SingleNodeExecutor.class);

  private final SingleComputationCycle _cycle;

  private final Map<CalculationJobSpecification, AtomicExecutorFuture> _executingSpecifications = new ConcurrentHashMap<CalculationJobSpecification, AtomicExecutorFuture>();

  public SingleNodeExecutor(SingleComputationCycle cycle) {
    ArgumentChecker.notNull(cycle, "Computation cycle");
    _cycle = cycle;
  }

  @Override
  public Future<ExecutionResult> execute(final DependencyGraph graph, final Queue<ExecutionResult> executionResultQueue, final GraphExecutorStatisticsGatherer statistics) {
    long jobId = JobIdSource.getId();
    CalculationJobSpecification jobSpec = new CalculationJobSpecification(_cycle.getUniqueId(), graph.getCalculationConfigurationName(), _cycle.getValuationTime(), jobId);
    List<DependencyNode> order = graph.getExecutionOrder();
    List<CalculationJobItem> items = new ArrayList<CalculationJobItem>();
    final Set<ValueSpecification> privateValues = new HashSet<ValueSpecification>();
    final Set<ValueSpecification> sharedValues = new HashSet<ValueSpecification>(graph.getTerminalOutputSpecifications());
    for (DependencyNode node : order) {
      final Set<ValueSpecification> inputs = node.getInputValues();
      final CalculationJobItem jobItem = new CalculationJobItem(node.getFunction().getFunction().getFunctionDefinition().getUniqueId(), node.getFunction().getParameters(),
          node.getComputationTarget(), inputs, node.getOutputValues());
      items.add(jobItem);
      // If node has dependencies which AREN'T in the graph, its outputs for those nodes are "shared" values
      for (ValueSpecification specification : node.getOutputValues()) {
        if (sharedValues.contains(specification)) {
          continue;
        }
        boolean isPrivate = true;
        for (DependencyNode dependent : node.getDependentNodes()) {
          if (!graph.containsNode(dependent)) {
            isPrivate = false;
            break;
          }
        }
        if (isPrivate) {
          privateValues.add(specification);
        } else {
          sharedValues.add(specification);
        }
      }
      // If node has inputs which haven't been seen already, they can't have been generated within this graph so are "shared"
      for (ValueSpecification specification : inputs) {
        if (sharedValues.contains(specification) || privateValues.contains(specification)) {
          continue;
        }
        sharedValues.add(specification);
      }
    }
    s_logger.debug("{} private values, {} shared values in graph", privateValues.size(), sharedValues.size());
    final CacheSelectHint cacheHint;
    if (privateValues.size() < sharedValues.size()) {
      cacheHint = CacheSelectHint.privateValues(privateValues);
    } else {
      cacheHint = CacheSelectHint.sharedValues(sharedValues);
    }
    s_logger.info("Enqueuing {} to invoke {} functions", new Object[] {jobSpec, items.size()});
    statistics.graphProcessed(graph.getCalculationConfigurationName(), 1, items.size(), Double.NaN, Double.NaN);
    AtomicExecutorCallable runnable = new AtomicExecutorCallable(executionResultQueue);
    AtomicExecutorFuture future = new AtomicExecutorFuture(runnable, order.toArray(new DependencyNode[order.size()]), statistics);
    _executingSpecifications.put(jobSpec, future);
    _cycle.getViewProcessContext().getViewProcessorQueryReceiver().addJob(jobSpec, graph);
    Cancelable cancel = _cycle.getViewProcessContext().getComputationJobDispatcher()
        .dispatchJob(new CalculationJob(jobSpec, _cycle.getFunctionInitId(), null, items, cacheHint), this);
    future.setCancel(cancel);

    return future;
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }

  @Override
  public void resultReceived(CalculationJobResult result) {
    AtomicExecutorFuture future = _executingSpecifications.remove(result.getSpecification());
    if (future == null) {
      s_logger.error("Got unexpected result {}", result);
      return;
    }
    try {
      int index = 0;
      for (CalculationJobResultItem item : result.getResultItems()) {
        DependencyNode node = future._nodes[index++];
        if (item.isFailed()) {
          _cycle.markFailed(node);
        } else {
          _cycle.markExecuted(node);
        }
      }
      // mark Future complete
      future._callable._result = new ExecutionResult(Collections.unmodifiableList(Arrays.asList(future._nodes)), result);
      future.run();
    } catch (RuntimeException e) {
      future._callable._exception = e;
      future.run();
    }
    future._statistics.graphExecuted(result.getSpecification().getCalcConfigName(), future._nodes.length, result.getDuration(), System.nanoTime() - future._startTime);
  }

  private class AtomicExecutorFuture extends FutureTask<ExecutionResult> {

    private final AtomicExecutorCallable _callable;
    private final DependencyNode[] _nodes;
    private final GraphExecutorStatisticsGatherer _statistics;
    private final long _startTime = System.nanoTime();
    private Cancelable _cancel;

    public AtomicExecutorFuture(AtomicExecutorCallable callable, DependencyNode[] nodes, GraphExecutorStatisticsGatherer statistics) {
      super(callable);
      _callable = callable;
      _nodes = nodes;
      _statistics = statistics;
    }

    public void setCancel(final Cancelable cancel) {
      _cancel = cancel;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      if (!_cancel.cancel(mayInterruptIfRunning)) {
        return false;
      }
      return super.cancel(mayInterruptIfRunning);
    }

  }

  private final class AtomicExecutorCallable implements Callable<ExecutionResult> {
    private RuntimeException _exception;
    private ExecutionResult _result;
    private final Queue<ExecutionResult> _executionResultQueue;

    private AtomicExecutorCallable(final Queue<ExecutionResult> executionResultQueue) {
      _executionResultQueue = executionResultQueue;
    }

    @Override
    public ExecutionResult call() throws Exception {
      if (_exception != null) {
        throw _exception;
      }
      if (_result == null) {
        throw new IllegalStateException("Result is null");
      }
      _executionResultQueue.add(_result);
      return _result;
    }
  }

}
