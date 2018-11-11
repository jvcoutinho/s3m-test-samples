/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.engine.view.compilation;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opengamma.OpenGammaRuntimeException;
import com.opengamma.core.position.Portfolio;
import com.opengamma.core.position.PortfolioNode;
import com.opengamma.core.position.Position;
import com.opengamma.core.position.PositionSource;
import com.opengamma.core.position.Trade;
import com.opengamma.core.position.impl.PortfolioImpl;
import com.opengamma.core.position.impl.PortfolioNodeTraverser;
import com.opengamma.core.security.Security;
import com.opengamma.core.security.SecuritySource;
import com.opengamma.engine.CachingComputationTargetResolver;
import com.opengamma.engine.depgraph.DependencyGraphBuilder;
import com.opengamma.engine.view.ResultModelDefinition;
import com.opengamma.engine.view.ResultOutputMode;
import com.opengamma.engine.view.ViewCalculationConfiguration;
import com.opengamma.engine.view.ViewDefinition;
import com.opengamma.id.UniqueIdentifier;
import com.opengamma.util.monitor.OperationTimer;

/**
 * Compiles Portfolio requirements into the dependency graphs.
 */
public final class PortfolioCompiler {

  private static final Logger s_logger = LoggerFactory.getLogger(PortfolioCompiler.class);

  private PortfolioCompiler() {
  }

  // --------------------------------------------------------------------------
  /**
   * Adds portfolio targets to the dependency graphs as required, and fully resolves the portfolio structure.
   * 
   * @param compilationContext  the context of the view definition compilation
   * @param forcePortfolioResolution  {@code true} if there are external portfolio targets, {@code false} otherwise
   * @return the fully-resolved portfolio structure if any portfolio targets were required, {@code null}
   *         otherwise.
   */
  protected static Portfolio execute(ViewCompilationContext compilationContext, boolean forcePortfolioResolution) {
    // Everything we do here is geared towards the avoidance of resolution (of portfolios, positions, securities)
    // wherever possible, to prevent needless dependencies (on a position master, security master) when a view never
    // really has them.

    if (!isPortfolioOutputEnabled(compilationContext.getViewDefinition())) {
      // Doesn't even matter if the portfolio can't be resolved - we're not outputting anything at the portfolio level
      // (which might be because the user knows the portfolio can't be resolved right now) so there are no portfolio
      // targets to add to the dependency graph.
      return null;
    }
     
    Portfolio portfolio = forcePortfolioResolution ? getPortfolio(compilationContext) : null;

    for (ViewCalculationConfiguration calcConfig : compilationContext.getViewDefinition().getAllCalculationConfigurations()) {
      if (calcConfig.getAllPortfolioRequirements().size() == 0) {
        // No portfolio requirements for this calculation configuration - avoid further processing.
        continue;
      }

      // Actually need the portfolio now
      if (portfolio == null) {
        portfolio = getPortfolio(compilationContext);
      }
      
      DependencyGraphBuilder builder = compilationContext.getBuilders().get(calcConfig.getName());

      // Cache PortfolioNode, Trade and Position entities
      CachingComputationTargetResolver resolver = compilationContext.getServices().getComputationTargetResolver();
      resolver.cachePortfolioNodeHierarchy(portfolio.getRootNode());
      cacheTradesPositionsAndSecurities(resolver, portfolio.getRootNode());

      // Add portfolio requirements to the dependency graph
      PortfolioCompilerTraversalCallback traversalCallback = new PortfolioCompilerTraversalCallback(builder, calcConfig);
      PortfolioNodeTraverser.depthFirst(traversalCallback).traverse(portfolio.getRootNode());
    }
    
    return portfolio;
  }

  private static void cacheTradesPositionsAndSecurities(final CachingComputationTargetResolver resolver, final PortfolioNode node) {
    final Collection<Position> positions = node.getPositions();
    resolver.cachePositions(positions);
    for (Position position : positions) {
      resolver.cacheSecurities(Collections.singleton(position.getSecurity()));
      for (Trade trade : position.getTrades()) {
        resolver.cacheSecurities(Collections.singleton(trade.getSecurity()));
      }
      resolver.cacheTrades(position.getTrades());
    }
    for (PortfolioNode child : node.getChildNodes()) {
      cacheTradesPositionsAndSecurities(resolver, child);
    }
  }

  // --------------------------------------------------------------------------
  
  /**
   * Tests whether the view has portfolio outputs enabled.
   * 
   * @param viewDefinition the view definition
   * @return {@code true} if there is at least one portfolio target, {@code false} otherwise
   */
  private static boolean isPortfolioOutputEnabled(ViewDefinition viewDefinition) {
    ResultModelDefinition resultModelDefinition = viewDefinition.getResultModelDefinition();
    return resultModelDefinition.getPositionOutputMode() != ResultOutputMode.NONE || resultModelDefinition.getAggregatePositionOutputMode() != ResultOutputMode.NONE;
  }

  /**
   * Fully resolves the portfolio structure for a view. A fully resolved structure has resolved
   * {@link Security} objects for each {@link Position} within the portfolio. Note however that
   * any underlying or related data referenced by a security will not be resolved at this stage. 
   * 
   * @param compilationContext  the compilation context containing the view being compiled, not null
   */
  private static Portfolio getPortfolio(ViewCompilationContext compilationContext) {
    UniqueIdentifier portfolioId = compilationContext.getViewDefinition().getPortfolioId();
    if (portfolioId == null) {
      throw new OpenGammaRuntimeException("The view definition '" + compilationContext.getViewDefinition().getName() + "' contains required portfolio outputs, but it does not reference a portfolio.");
    }
    PositionSource positionSource = compilationContext.getServices().getPositionSource();
    if (positionSource == null) {
      throw new OpenGammaRuntimeException("The view definition '" + compilationContext.getViewDefinition().getName()
          + "' contains required portfolio outputs, but the compiler does not have access to a position source.");
    }
    Portfolio portfolio = positionSource.getPortfolio(portfolioId);
    if (portfolio == null) {
      throw new OpenGammaRuntimeException("Unable to resolve portfolio '" + portfolioId + "' in position source '" + positionSource + "' used by view definition '"
          + compilationContext.getViewDefinition().getName() + "'");
    }
    Portfolio cloned = new PortfolioImpl(portfolio);
    return resolveSecurities(compilationContext, cloned);
  }

  /**
   * Resolves the securities.
   * 
   * @param compilationContext  the compilation context containing the view being compiled, not null
   * @param portfolio  the portfolio to update, not null
   * @return the updated portfolio, not null
   */
  private static Portfolio resolveSecurities(ViewCompilationContext compilationContext, Portfolio portfolio) {
    OperationTimer timer = new OperationTimer(s_logger, "Resolving all securities for {}", portfolio.getName());
    try {
      new SecurityLinkResolver(compilationContext).resolveSecurities(portfolio.getRootNode());
    } catch (Exception e) {
      throw new OpenGammaRuntimeException("Unable to resolve all securities for portfolio " + portfolio.getName());
    } finally {
      timer.finished();
    }
    return portfolio;
  }

  //-------------------------------------------------------------------------
  /**
   * Resolves the securities in the portfolio.
   * 
   * @param portfolio  the portfolio to resolve, not null
   * @param executorService  the threading service, not null
   * @param securitySource  the security source, not null
   * @return the resolved portfolio, not null
   */
  public static Portfolio resolvePortfolio(final Portfolio portfolio, final ExecutorService executorService, final SecuritySource securitySource) {
    Portfolio cloned = new PortfolioImpl(portfolio);
    new SecurityLinkResolver(executorService, securitySource).resolveSecurities(portfolio.getRootNode());
    return cloned;
  }

}
